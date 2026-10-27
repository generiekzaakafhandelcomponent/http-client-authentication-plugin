/*
 * Copyright 2015-2026 Ritense BV, the Netherlands.
 *
 * Licensed under EUPL, Version 1.2 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.ritense.valtimoplugins.httpclientauthentication.plugin

import com.ritense.plugin.annotation.Plugin
import com.ritense.plugin.domain.PluginConfigurationId
import com.ritense.valtimoplugins.httpclientauthentication.saml.SamlTokenClient
import com.ritense.valtimoplugins.httpclientauthentication.tls.TlsRequestFactoryCache
import com.ritense.valtimoplugins.httpclientauthentication.tokenexchange.TokenExchangeTokenCache
import net.bytebuddy.ByteBuddy
import net.bytebuddy.description.annotation.AnnotationDescription
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy
import net.bytebuddy.implementation.DefaultMethodCall
import net.bytebuddy.matcher.ElementMatchers.named
import net.bytebuddy.matcher.ElementMatchers.returns
import net.bytebuddy.matcher.ElementMatchers.takesArguments
import java.lang.invoke.MethodHandles
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Generates the `@Plugin` class: a subclass of [HttpClientAuthenticationPluginBase] that implements the given
 * interfaces. The properties are fields of the base class.
 */
object PluginClassGenerator {
    /**
     * @param className must be in the package of [lookup]'s class, which defines it in its class loader
     */
    fun generate(
        interfaces: List<Class<*>>,
        className: String,
        lookup: MethodHandles.Lookup,
    ): Class<out HttpClientAuthenticationPluginBase> {
        var builder =
            ByteBuddy()
                .subclass(
                    HttpClientAuthenticationPluginBase::class.java,
                    ConstructorStrategy.Default.IMITATE_SUPER_CLASS_PUBLIC,
                ).name(className)
                .implement(interfaces)
                .annotateType(pluginAnnotation())
        // Two interfaces with a default for the same method: the JVM refuses to choose, so the class does
        conflictingDefaults(interfaces).forEach { (method, owners) ->
            builder =
                builder
                    .method(
                        named<net.bytebuddy.description.method.MethodDescription>(method.name)
                            .and(takesArguments(*method.parameterTypes))
                            .and(returns(method.returnType)),
                    ).intercept(DefaultMethodCall.prioritize(owners.first()))
        }
        return builder
            .make()
            .load(lookup.lookupClass().classLoader, ClassLoadingStrategy.UsingLookup.of(lookup))
            .loaded
    }

    /** The constructor of a generated class, which takes the base class's dependencies. */
    fun constructorOf(
        pluginClass: Class<out HttpClientAuthenticationPluginBase>,
    ): Constructor<out HttpClientAuthenticationPluginBase> =
        pluginClass.getConstructor(
            PluginConfigurationId::class.java,
            TlsRequestFactoryCache::class.java,
            TokenExchangeTokenCache::class.java,
            SamlTokenClient::class.java,
        )

    private fun pluginAnnotation(): AnnotationDescription =
        AnnotationDescription.Builder
            .ofType(Plugin::class.java)
            .define("key", HttpClientAuthenticationPluginBase.PLUGIN_KEY)
            .define("title", HttpClientAuthenticationPluginBase.PLUGIN_TITLE)
            .define("description", HttpClientAuthenticationPluginBase.PLUGIN_DESCRIPTION)
            .build()

    /** Methods that the base class doesn't implement and more than one interface provides a default for. */
    internal fun conflictingDefaults(interfaces: List<Class<*>>): List<Pair<Method, List<Class<*>>>> {
        val defaults =
            interfaces
                .flatMap(::withSuperinterfaces)
                .distinct()
                .flatMap { it.declaredMethods.filter(Method::isDefault) }
                .groupBy { signatureOf(it) }
        return defaults.values
            .filterNot { implementedByBase(it.first()) }
            .map { methods ->
                // A default of a subinterface overrides that of its superinterface
                val owners = methods.map { it.declaringClass }
                val mostSpecific = owners.filter { owner -> owners.none { it != owner && owner.isAssignableFrom(it) } }
                methods.first() to mostSpecific
            }.filter { (_, owners) -> owners.size > 1 }
    }

    private fun withSuperinterfaces(type: Class<*>): List<Class<*>> =
        listOf(type) + type.interfaces.flatMap(::withSuperinterfaces)

    private fun signatureOf(method: Method) =
        method.name + method.parameterTypes.joinToString(",", "(", ")") { it.name }

    private fun implementedByBase(method: Method): Boolean =
        try {
            val implementation =
                HttpClientAuthenticationPluginBase::class.java.getMethod(method.name, *method.parameterTypes)
            !Modifier.isAbstract(implementation.modifiers) && !implementation.declaringClass.isInterface
        } catch (e: NoSuchMethodException) {
            false
        }
}

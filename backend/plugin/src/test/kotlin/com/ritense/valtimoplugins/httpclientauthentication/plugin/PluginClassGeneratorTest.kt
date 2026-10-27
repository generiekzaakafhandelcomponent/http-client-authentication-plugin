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
import com.ritense.plugin.annotation.PluginEvent
import com.ritense.valtimoplugins.haalcentraalauth.HaalCentraalAuthentication
import com.ritense.valtimoplugins.httpclientauthentication.HttpClientAuthenticator
import com.ritense.valtimoplugins.httpclientauthentication.model.AuthenticationType
import org.junit.jupiter.api.Test
import org.springframework.util.ClassUtils
import java.lang.invoke.MethodHandles
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.ritense.valtimoplugins.haalcentraalauthenticationplugin.HaalCentraalAuthentication as HaalCentraalSamlAuthentication

internal class PluginClassGeneratorTest {
    // Those of this Valtimo version
    private val coveredInterfaces =
        CoveredInterfaces.ALL
            .filter { ClassUtils.isPresent(it.interfaceName, null) }
            .map { Class.forName(it.implementedBy) }

    @Test
    fun `should generate a working plugin class for no covered interface, each alone and all together`() {
        val combinations =
            listOf(emptyList<Class<*>>()) + coveredInterfaces.map { listOf(it) } + listOf(coveredInterfaces)

        combinations.forEach { interfaces ->
            val pluginClass = generate(interfaces)
            val description = "${pluginClass.name} with ${interfaces.map { it.simpleName }}"

            val annotation = pluginClass.getAnnotation(Plugin::class.java)
            assertEquals(HttpClientAuthenticationPluginBase.PLUGIN_KEY, annotation.key, description)
            assertEquals(HttpClientAuthenticationPluginBase.PLUGIN_TITLE, annotation.title, description)
            assertEquals(HttpClientAuthenticationPluginBase.PLUGIN_DESCRIPTION, annotation.description, description)
            assertEquals(interfaces.toSet(), pluginClass.interfaces.toSet(), description)
            assertTrue(HttpClientAuthenticator::class.java.isAssignableFrom(pluginClass), description)
            // The compiler used to check this for the build-time variants
            assertEquals(
                emptyList(),
                pluginClass.methods
                    .filter {
                        Modifier.isAbstract(it.modifiers)
                    }.map { it.toString() },
                description,
            )
            // Valtimo runs the plugin events through Kotlin reflection
            val validate = pluginClass.kotlin.functions.single { it.findAnnotation<PluginEvent>() != null }
            val plugin = newInstance(pluginClass).also { it.authenticationType = AuthenticationType.NONE }
            validate.call(plugin)
        }
    }

    @Test
    fun `should choose between two defaults of the same method`() {
        val haalCentraalSupports =
            listOf(
                Class.forName("com.ritense.valtimoplugins.httpclientauthentication.support.HaalCentraalAuthSupport"),
                Class.forName(
                    "com.ritense.valtimoplugins.httpclientauthentication.support.HaalCentraalAuthenticationPluginSupport",
                ),
            )
        val plugin =
            newInstance(
                generate(haalCentraalSupports),
            ).also { it.authenticationType = AuthenticationType.NONE }

        assertNotNull((plugin as HaalCentraalAuthentication).getAuthenticatedHttpClient())
        assertNotNull((plugin as HaalCentraalSamlAuthentication).getAuthenticatedHttpClient())
        assertEquals(
            listOf(
                "getAuthenticatedHttpClient",
            ),
            PluginClassGenerator.conflictingDefaults(haalCentraalSupports).map {
                it.first.name
            },
        )
    }

    @Test
    fun `should not see a conflict in a single interface or in methods of the base class`() {
        assertEquals(emptyList(), coveredInterfaces.flatMap { PluginClassGenerator.conflictingDefaults(listOf(it)) })
    }

    @Test
    fun `should generate the plugin class of this class loader once, for every covered interface on the classpath`() {
        val pluginClass = GeneratedPluginClass.get()

        assertEquals(GeneratedPluginClass.CLASS_NAME, pluginClass.name)
        assertEquals(pluginClass, GeneratedPluginClass.get())
        // Valtimo loads it by name
        assertEquals(pluginClass, Class.forName(GeneratedPluginClass.CLASS_NAME))
        assertEquals(coveredInterfaces.toSet(), pluginClass.interfaces.toSet())
    }

    private fun generate(interfaces: List<Class<*>>) =
        PluginClassGenerator.generate(
            interfaces,
            "$PACKAGE.TestPlugin${counter.incrementAndGet()}",
            MethodHandles.lookup(),
        )

    private fun newInstance(pluginClass: Class<out HttpClientAuthenticationPluginBase>) =
        newPlugin(pluginClass = pluginClass)

    companion object {
        private const val PACKAGE = "com.ritense.valtimoplugins.httpclientauthentication.plugin"
        private val counter = AtomicInteger()
    }
}

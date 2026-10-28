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

package com.ritense.valtimoplugins.httpclientauthentication.deployment

import com.ritense.plugin.annotation.Plugin
import com.ritense.plugin.annotation.PluginProperty
import com.ritense.plugin.domain.PluginCategory
import com.ritense.plugin.domain.PluginDefinition
import com.ritense.plugin.repository.PluginCategoryRepository
import com.ritense.plugin.repository.PluginDefinitionRepository
import com.ritense.valtimoplugins.httpclientauthentication.plugin.GeneratedPluginClass
import com.ritense.valtimoplugins.httpclientauthentication.plugin.HttpClientAuthenticationPluginBase
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.transaction.annotation.Transactional
import java.lang.reflect.Field
import com.ritense.plugin.annotation.PluginCategory as PluginCategoryAnnotation

/**
 * Deploys the definition of the generated plugin class the way Valtimo's `PluginDeploymentListener` deploys a scanned
 * one: Valtimo's scan only finds class files. Saves the categories itself, so it doesn't depend on running after
 * Valtimo's listener.
 */
open class HttpClientAuthenticationPluginDeployer(
    private val pluginDefinitionRepository: PluginDefinitionRepository,
    private val pluginCategoryRepository: PluginCategoryRepository,
    private val pluginClass: Class<out HttpClientAuthenticationPluginBase> = GeneratedPluginClass.get(),
) {
    @Transactional
    @EventListener(ApplicationStartedEvent::class)
    open fun deploy() {
        checkNoScannedClassWithKey()
        val plugin = pluginClass.getAnnotation(Plugin::class.java)
        logger.info { "Deploying plugin ${plugin.key} as ${pluginClass.name}" }
        val definition =
            PluginDefinition(
                key = plugin.key,
                title = plugin.title,
                description = plugin.description,
                fullyQualifiedClassName = pluginClass.name,
                properties = mutableSetOf(),
                categories = mutableSetOf(),
            )
        categoriesOf(pluginClass).forEach { (key, type) ->
            val category =
                pluginCategoryRepository.findById(key).orElseGet {
                    pluginCategoryRepository.save(PluginCategory(key, type.name))
                }
            definition.addCategory(category)
        }
        propertyFieldsOf(pluginClass).forEach { field ->
            definition.addProperty(field, field.getAnnotation(PluginProperty::class.java))
        }
        pluginDefinitionRepository.save(definition)
    }

    // Deployed by Valtimo before this listener; one deployed after it is found by PluginDefinitionCheck
    private fun checkNoScannedClassWithKey() {
        val deployedClassName =
            pluginDefinitionRepository.findById(PLUGIN_KEY).map { it.fullyQualifiedClassName }.orElse(null)
        if (deployedClassName == null || deployedClassName == pluginClass.name) {
            return
        }
        // Classes of earlier versions of this plugin no longer exist
        val deployedClass =
            try {
                Class.forName(deployedClassName, false, pluginClass.classLoader)
            } catch (e: ClassNotFoundException) {
                return
            } catch (e: LinkageError) {
                return
            }
        check(deployedClass.getAnnotation(Plugin::class.java)?.key != PLUGIN_KEY) {
            "Plugin '$PLUGIN_KEY' is also declared by $deployedClassName, which Valtimo deployed: " +
                "remove the library that contains it (e.g. another copy of http-client-authentication)"
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        private const val PLUGIN_KEY = HttpClientAuthenticationPluginBase.PLUGIN_KEY

        /** The category keys, with the interface that declares each, like Valtimo links them. */
        private fun categoriesOf(type: Class<*>): Map<String, Class<*>> {
            val categories = linkedMapOf<String, Class<*>>()

            fun collect(current: Class<*>) {
                current
                    .getAnnotation(
                        PluginCategoryAnnotation::class.java,
                    )?.let { categories.putIfAbsent(it.key, current) }
                current.superclass?.let(::collect)
                current.interfaces.forEach(::collect)
            }
            collect(type)
            return categories
        }

        private fun propertyFieldsOf(type: Class<*>): List<Field> =
            generateSequence(type) { it.superclass }
                .flatMap { it.declaredFields.asSequence() }
                .filter { it.isAnnotationPresent(PluginProperty::class.java) }
                .toList()
    }
}

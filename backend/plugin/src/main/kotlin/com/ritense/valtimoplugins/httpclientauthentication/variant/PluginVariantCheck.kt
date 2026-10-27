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

package com.ritense.valtimoplugins.httpclientauthentication.variant

import com.ritense.plugin.repository.PluginDefinitionRepository
import com.ritense.valtimoplugins.httpclientauthentication.plugin.HttpClientAuthenticationPluginBase.Companion.PLUGIN_KEY
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

/**
 * Stops the application when Valtimo deployed another class for the plugin key than the selected variant, for example
 * because the application's own packages include all variants. Plugin configurations wouldn't load otherwise.
 */
class PluginVariantCheck(
    private val pluginDefinitionRepository: PluginDefinitionRepository,
    private val variant: PluginVariant = PluginVariants.selected,
) {
    // Valtimo deploys the definitions when the application has started, and the configurations when it's ready
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @EventListener(ApplicationReadyEvent::class)
    fun checkDeployedVariant() {
        val deployedClassName = pluginDefinitionRepository.findById(PLUGIN_KEY).map { it.fullyQualifiedClassName }
        check(deployedClassName.orElse(null) == variant.className) {
            "Plugin '$PLUGIN_KEY' is deployed with class ${deployedClassName.orElse("<none>")}, but the ZGW " +
                "modules on the classpath need ${variant.className}. Of ${PluginVariants.ROOT_PACKAGE}, Valtimo " +
                "may only scan ${variant.packageName}: check valtimo.annotation-scan.accepted-packages and the " +
                "package of the @SpringBootApplication class."
        }
    }
}

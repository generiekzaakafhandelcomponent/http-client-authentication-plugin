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

import org.springframework.boot.SpringApplication
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.Ordered
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

/**
 * Adds the package of the selected variant to the packages Valtimo scans for `@Plugin` classes, keeping the packages
 * the application configured.
 */
class PluginVariantEnvironmentPostProcessor :
    EnvironmentPostProcessor,
    Ordered {
    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment,
        application: SpringApplication?,
    ) = addVariantPackage(environment, PluginVariants.selected)

    internal fun addVariantPackage(
        environment: ConfigurableEnvironment,
        variant: PluginVariant,
    ) {
        // Bound instead of read, so both a list and a comma-separated value are found
        val acceptedPackages =
            Binder
                .get(environment)
                .bind(ACCEPTED_PACKAGES, Bindable.listOf(String::class.java))
                .orElse(emptyList())
        val variantPackage = variant.packageName
        if (variantPackage in acceptedPackages) {
            return
        }
        // Valtimo reads the property as a single comma-separated value, which an indexed list doesn't provide
        environment.propertySources.addFirst(
            MapPropertySource(
                PROPERTY_SOURCE_NAME,
                mapOf(ACCEPTED_PACKAGES to (acceptedPackages + variantPackage).joinToString(",")),
            ),
        )
    }

    // After the application's configuration is loaded
    override fun getOrder() = Ordered.LOWEST_PRECEDENCE

    companion object {
        const val ACCEPTED_PACKAGES = "valtimo.annotation-scan.accepted-packages"
        const val PROPERTY_SOURCE_NAME = "httpClientAuthenticationPluginVariant"
    }
}

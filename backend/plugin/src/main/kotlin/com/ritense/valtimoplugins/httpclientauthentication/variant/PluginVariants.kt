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

import org.springframework.util.ClassUtils

/**
 * The build generates a `@Plugin` class for every combination of the ZGW authentication interfaces, each in its own
 * package under [ROOT_PACKAGE], named after the combination: one bit per interface in [ZGW_AUTHENTICATION_INTERFACES],
 * e.g. `z00000110` for Objecten and Objecttypen API. Valtimo only scans the package of the selected variant, so it
 * only sees one class for the plugin key.
 */
object PluginVariants {
    // Outside com.ritense, which Valtimo always scans
    const val ROOT_PACKAGE = VARIANTS_ROOT_PACKAGE
    const val CLASS_NAME = VARIANT_CLASS_NAME

    /** The variant for the ZGW modules on this plugin's classpath. */
    val selected: PluginVariant by lazy {
        select { ClassUtils.isPresent(it, PluginVariants::class.java.classLoader) }
    }

    fun select(isPresent: (String) -> Boolean): PluginVariant =
        PluginVariant(ZGW_AUTHENTICATION_INTERFACES.joinToString("") { if (isPresent(it)) "1" else "0" })
}

data class PluginVariant(
    val bits: String,
) {
    val packageName: String
        get() = "${PluginVariants.ROOT_PACKAGE}.z$bits"

    val className: String
        get() = "$packageName.${PluginVariants.CLASS_NAME}"

    val zgwAuthenticationInterfaces: List<String>
        get() = ZGW_AUTHENTICATION_INTERFACES.filterIndexed { index, _ -> bits[index] == '1' }
}

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

import org.springframework.util.ClassUtils
import java.lang.invoke.MethodHandles

/**
 * The plugin class of this class loader, generated on first use for the covered interfaces on the classpath. Valtimo
 * only finds `@Plugin` classes that exist as class files, so the plugin deploys its definition itself.
 */
object GeneratedPluginClass {
    // Never published as a class file: older versions and the monorepo copy have …plugin.HttpClientAuthenticationPlugin
    const val CLASS_NAME =
        "com.ritense.valtimoplugins.httpclientauthentication.plugin.GeneratedHttpClientAuthenticationPlugin"

    private val pluginClass by lazy {
        val classLoader = javaClass.classLoader
        val interfaces =
            CoveredInterfaces
                .present({ ClassUtils.isPresent(it, classLoader) })
                .map { Class.forName(it, true, classLoader) }
        PluginClassGenerator.generate(interfaces, CLASS_NAME, MethodHandles.lookup())
    }

    fun get(): Class<out HttpClientAuthenticationPluginBase> = pluginClass
}

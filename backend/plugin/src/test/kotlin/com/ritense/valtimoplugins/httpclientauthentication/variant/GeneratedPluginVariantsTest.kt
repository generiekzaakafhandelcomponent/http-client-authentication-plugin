/*
 * Copyright 2026 Ritense BV, the Netherlands.
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
 */

package com.ritense.valtimoplugins.httpclientauthentication.variant

import com.ritense.plugin.annotation.Plugin
import com.ritense.plugin.annotation.PluginProperty
import com.ritense.plugin.domain.PluginConfigurationId
import com.ritense.valtimo.contract.client.ValtimoHttpRestClientConfigurationProperties
import com.ritense.valtimoplugins.httpclientauthentication.HttpClientAuthenticator
import com.ritense.valtimoplugins.httpclientauthentication.plugin.HttpClientAuthenticationPluginBase
import com.ritense.valtimoplugins.httpclientauthentication.tls.TlsRequestFactoryCache
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class GeneratedPluginVariantsTest {
    private val variants =
        (0 until 256).map { PluginVariant(Integer.toBinaryString(it).padStart(8, '0')) }

    @Test
    fun `should generate a plugin class with the same key for every combination of ZGW interfaces`() {
        variants.forEach { variant ->
            val pluginClass = Class.forName(variant.className)
            val annotation = pluginClass.getAnnotation(Plugin::class.java)

            assertEquals(HttpClientAuthenticationPluginBase.PLUGIN_KEY, annotation.key, variant.className)
            assertEquals(HttpClientAuthenticationPluginBase.PLUGIN_TITLE, annotation.title, variant.className)
            assertTrue(HttpClientAuthenticationPluginBase::class.java.isAssignableFrom(pluginClass))
            assertTrue(HttpClientAuthenticator::class.java.isAssignableFrom(pluginClass))
        }
    }

    @Test
    fun `should implement exactly the ZGW interfaces of the combination`() {
        variants.forEach { variant ->
            assertEquals(
                variant.zgwAuthenticationInterfaces.toSet(),
                Class
                    .forName(variant.className)
                    .interfaces
                    .map { it.name }
                    .toSet(),
                variant.className,
            )
        }
    }

    @Test
    fun `should declare the plugin properties on every variant, where Valtimo looks for them`() {
        variants.forEach { variant ->
            val properties =
                Class
                    .forName(variant.className)
                    .declaredFields
                    .mapNotNull { field -> field.getAnnotation(PluginProperty::class.java)?.let { field.name to it } }
                    .toMap()

            assertEquals(
                mapOf(
                    "authenticationType" to (true to false),
                    "authHeaderName" to (false to false),
                    "authSecret" to (false to true),
                    "clientId" to (false to false),
                    "clientCertificate" to (false to false),
                    "clientPrivateKey" to (false to true),
                    "serverCertificate" to (false to false),
                ),
                properties.mapValues { (_, property) -> property.required to property.secret },
                variant.className,
            )
            properties.forEach { (name, property) -> assertEquals(name, property.key) }
        }
    }

    @Test
    fun `should create every variant with the constructor the factory uses`() {
        val cache = TlsRequestFactoryCache(ValtimoHttpRestClientConfigurationProperties())
        val configurationId = PluginConfigurationId.newId()

        variants.forEach { variant ->
            val plugin =
                Class
                    .forName(variant.className)
                    .getConstructor(PluginConfigurationId::class.java, TlsRequestFactoryCache::class.java)
                    .newInstance(configurationId, cache) as HttpClientAuthenticationPluginBase

            assertEquals(configurationId, plugin.configurationId)
        }
    }
}

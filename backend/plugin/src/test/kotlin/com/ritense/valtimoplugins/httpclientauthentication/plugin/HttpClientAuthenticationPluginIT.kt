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

package com.ritense.valtimoplugins.httpclientauthentication.plugin

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.ritense.plugin.service.PluginService
import com.ritense.valtimoplugins.httpclientauthentication.BaseIntegrationTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Transactional
internal class HttpClientAuthenticationPluginIT : BaseIntegrationTest() {

    @Autowired
    lateinit var pluginService: PluginService

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `should save a complete TOKEN_EXCHANGE configuration`() {
        val configuration = pluginService.createPluginConfiguration(
            "token exchange",
            properties(
                """
                {
                    "authenticationType": "TOKEN_EXCHANGE",
                    "tokenEndpoint": "https://keycloak.example.com/token",
                    "clientId": "client",
                    "clientSecret": "secret",
                    "audience": "haal-centraal"
                }
                """
            ),
            PLUGIN_KEY,
        )

        val plugin = pluginService.createInstance(configuration) as HttpClientAuthenticationPlugin
        assertEquals("haal-centraal", plugin.audience)
    }

    @Test
    fun `should reject saving a configuration that misses the properties of its type`() {
        val exception = assertThrows<Exception> {
            pluginService.createPluginConfiguration(
                "incomplete bearer",
                properties("""{ "authenticationType": "BEARER" }"""),
                PLUGIN_KEY,
            )
        }

        assertTrue(
            generateSequence<Throwable>(exception) { it.cause }.any { it.message?.contains("authSecret") == true },
            "Expected the validation message to mention authSecret, got: $exception",
        )
    }

    private fun properties(json: String) = objectMapper.readTree(json) as ObjectNode

    companion object {
        private const val PLUGIN_KEY = "http-client-authentication-plugin"
    }
}

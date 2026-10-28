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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.ritense.documentenapi.DocumentenApiAuthentication
import com.ritense.plugin.domain.PluginConfiguration
import com.ritense.plugin.repository.PluginDefinitionRepository
import com.ritense.plugin.service.PluginConfigurationSearchParameters
import com.ritense.plugin.service.PluginService
import com.ritense.tokenauthentication.plugin.TokenAuthentication
import com.ritense.valtimoplugins.haalcentraalauth.HaalCentraalAuthentication
import com.ritense.valtimoplugins.httpclientauthentication.BaseIntegrationTest
import com.ritense.valtimoplugins.httpclientauthentication.HttpClientAuthenticator
import com.ritense.valtimoplugins.httpclientauthentication.plugin.HttpClientAuthenticationPluginBase.Companion.PLUGIN_KEY
import com.ritense.valtimoplugins.httpclientauthentication.tls.TlsFixtures
import com.ritense.valtimoplugins.mtlssslcontext.MTlsSslContext
import com.ritense.valtimoplugins.suwinetauth.plugin.SuwinetAuth
import com.ritense.valtimoplugins.tokenexchangeauth.TokenExchangeAuthentication
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.ritense.valtimoplugins.haalcentraalauthenticationplugin.HaalCentraalAuthentication as HaalCentraalSamlAuthentication

// The application has every covered auth plugin next to this one
internal class CoveredPluginsIT : BaseIntegrationTest() {
    @Autowired
    lateinit var pluginService: PluginService

    @Autowired
    lateinit var pluginDefinitionRepository: PluginDefinitionRepository

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    @Transactional
    fun `should deploy the covered auth plugins next to this one, each under its own key`() {
        COVERED_PLUGIN_KEYS.forEach { key -> assertTrue(pluginDefinitionRepository.existsById(key), key) }
        assertEquals(
            GeneratedPluginClass.CLASS_NAME,
            pluginDefinitionRepository.findById(PLUGIN_KEY).orElseThrow().fullyQualifiedClassName,
        )
    }

    @Test
    @Transactional
    fun `should give a consumer of any covered category an instance of its interface`() {
        val configuration =
            createConfiguration(
                PLUGIN_KEY,
                mapOf(
                    "authenticationType" to "BEARER",
                    "authSecret" to "token",
                ),
            )

        val instance = pluginService.createInstance(configuration.id)

        listOf(
            HttpClientAuthenticator::class,
            DocumentenApiAuthentication::class,
            TokenAuthentication::class,
            HaalCentraalAuthentication::class,
            HaalCentraalSamlAuthentication::class,
            SuwinetAuth::class,
            TokenExchangeAuthentication::class,
            MTlsSslContext::class,
        ).forEach { type -> assertTrue(type.isInstance(instance), type.simpleName) }
        assertEquals("token", (instance as TokenExchangeAuthentication).getAccessToken())
    }

    @Test
    @Transactional
    fun `should list this plugin's configurations for every covered category`() {
        val configuration = createConfiguration(PLUGIN_KEY, mapOf("authenticationType" to "NONE"))

        COVERED_CATEGORIES.forEach { category ->
            val ids =
                pluginService
                    .getPluginConfigurations(
                        PluginConfigurationSearchParameters(category = category),
                    ).map { it.id }
            assertTrue(configuration.id in ids, category)
        }
    }

    @Test
    @Transactional
    fun `should keep the covered auth plugins working next to this one`() {
        val tokenAuthentication = instance("tokenauthentication", mapOf("token" to "abc")) as TokenAuthentication
        val haalCentraalAuth =
            instance(
                "haal-centraal-auth",
                mapOf("authenticationSecret" to "secret"),
            ) as HaalCentraalAuthentication
        val mtls =
            instance(
                "mTLS-SSLContext",
                mapOf(
                    "base64ServerCert" to base64(TlsFixtures.CA_CERTIFICATE),
                    "base64ClientCert" to base64(TlsFixtures.CLIENT_CERTIFICATE),
                    "base64PrivateKey" to base64(TlsFixtures.CLIENT_PRIVATE_KEY),
                ),
            ) as MTlsSslContext
        val tokenExchange =
            instance(
                "token-exchange-authentication",
                mapOf(
                    "tokenEndpoint" to "https://keycloak.example.com/token",
                    "clientId" to "client",
                    "clientSecret" to "secret",
                    "audience" to "audience",
                ),
            )

        assertNotNull(tokenAuthentication.applyAuth(RestClient.builder()))
        assertNotNull(haalCentraalAuth.applyAuth(RestClient.builder()))
        assertNotNull(mtls.createSslContext())
        assertTrue(instance("suwinet-auth", mapOf("authType" to "EXTERN")) is SuwinetAuth)
        assertTrue(
            instance("haal-centraal-authentication-plugin", mapOf("tokenServiceUrl" to "https://sts.example.com"))
                is HaalCentraalSamlAuthentication,
        )
        assertTrue(tokenExchange is TokenExchangeAuthentication)
    }

    private fun instance(
        key: String,
        properties: Map<String, String>,
    ): Any = pluginService.createInstance(createConfiguration(key, properties).id)

    private fun base64(text: String) = Base64.getEncoder().encodeToString(text.toByteArray())

    private fun createConfiguration(
        key: String,
        properties: Map<String, String>,
    ): PluginConfiguration =
        pluginService.createPluginConfiguration(
            "$key ${UUID.randomUUID()}",
            objectMapper.valueToTree<ObjectNode>(properties),
            key,
        )

    companion object {
        private val COVERED_PLUGIN_KEYS =
            listOf(
                "haal-centraal-auth",
                "haal-centraal-authentication-plugin",
                "suwinet-auth",
                "tokenauthentication",
                "token-exchange-authentication",
                "mTLS-SSLContext",
            )
        private val COVERED_CATEGORIES =
            listOf(
                "valtimo-token-authentication-configuration",
                "haal-centraal-auth",
                // The category of haal-centraal-authentication-plugin 1.0.1-V12
                "haal-centraal-authentication",
                "suwinet-authentication",
                "token-exchange-authentication",
                "mtls-sslcontext-plugin",
                "documenten-api-authentication",
            )
    }
}

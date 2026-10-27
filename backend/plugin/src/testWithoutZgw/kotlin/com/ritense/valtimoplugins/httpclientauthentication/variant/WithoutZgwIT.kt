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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.ritense.plugin.domain.PluginConfiguration
import com.ritense.plugin.exception.PluginEventInvocationException
import com.ritense.plugin.repository.PluginDefinitionRepository
import com.ritense.plugin.service.PluginService
import com.ritense.valtimoplugins.httpclientauthentication.BaseIntegrationTest
import com.ritense.valtimoplugins.httpclientauthentication.HttpClientAuthenticator
import com.ritense.valtimoplugins.httpclientauthentication.plugin.HttpClientAuthenticationPluginBase.Companion.PLUGIN_KEY
import com.ritense.valtimoplugins.httpclientauthentication.tls.MtlsTestServer
import com.ritense.valtimoplugins.httpclientauthentication.tls.TlsFixtures
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.ClassUtils
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Runs on the classpath of an application without any ZGW module
internal class WithoutZgwIT : BaseIntegrationTest() {
    @Autowired
    lateinit var pluginService: PluginService

    @Autowired
    lateinit var pluginDefinitionRepository: PluginDefinitionRepository

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `should run without ZGW modules`() {
        ZGW_MODULE_CLASSES.forEach { assertFalse(ClassUtils.isPresent(it, null), it) }
    }

    @Test
    @Transactional
    fun `should deploy the variant without ZGW interfaces`() {
        val definition = pluginDefinitionRepository.findById(PLUGIN_KEY).orElseThrow()

        assertEquals(
            "valtimoplugins.httpclientauthentication.variant.z0000000.HttpClientAuthenticationPlugin",
            definition.fullyQualifiedClassName,
        )
        assertEquals(setOf("http-client-authentication"), definition.categories.map { it.key }.toSet())
        assertEquals(15, definition.properties.size)
    }

    @Test
    @Transactional
    fun `should present client certificate and token to a server that requires mutual TLS`() {
        MtlsTestServer().use { server ->
            val configuration =
                createConfiguration(
                    mapOf(
                        "authenticationType" to "TOKEN",
                        "authSecret" to "1e2804e1113e083fb1f8258d161a2e222c317987",
                        "clientCertificate" to TlsFixtures.CLIENT_CERTIFICATE,
                        "clientPrivateKey" to TlsFixtures.CLIENT_PRIVATE_KEY,
                        "serverCertificate" to TlsFixtures.CA_CERTIFICATE,
                    ),
                )
            val authenticator = pluginService.createInstance(configuration.id) as HttpClientAuthenticator

            val body = call(authenticator, server.baseUrl)

            assertEquals("{}", body)
            assertEquals(TlsFixtures.CLIENT_SUBJECT, server.receivedClientCertificateSubject)
            assertEquals("Token 1e2804e1113e083fb1f8258d161a2e222c317987", server.receivedAuthorization)
        }
    }

    @Test
    @Transactional
    fun `should send a ZGW JWT without the ZGW modules`() {
        MtlsTestServer().use { server ->
            val configuration =
                createConfiguration(
                    mapOf(
                        "authenticationType" to "ZGW_JWT",
                        "clientId" to "valtimo",
                        "authSecret" to "c3e8a1f5d2b74e9a8f6c0b4d7e2a9f1c",
                        "clientCertificate" to TlsFixtures.CLIENT_CERTIFICATE,
                        "clientPrivateKey" to TlsFixtures.CLIENT_PRIVATE_KEY,
                        "serverCertificate" to TlsFixtures.CA_CERTIFICATE,
                    ),
                )
            val authenticator = pluginService.createInstance(configuration.id) as HttpClientAuthenticator

            call(authenticator, server.baseUrl)

            assertTrue(server.receivedAuthorization!!.startsWith("Bearer "))
        }
    }

    @Test
    fun `should refuse to save a configuration whose private key does not belong to the client certificate`() {
        val exception =
            assertThrows<PluginEventInvocationException> {
                createConfiguration(
                    mapOf(
                        "authenticationType" to "NONE",
                        "clientCertificate" to TlsFixtures.CLIENT_CERTIFICATE,
                        "clientPrivateKey" to TlsFixtures.OTHER_PRIVATE_KEY,
                    ),
                )
            }

        val messages = generateSequence(exception as Throwable) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue(
            messages.any {
                it.contains("Property 'clientPrivateKey' does not belong to the certificate in 'clientCertificate'")
            },
            messages.joinToString(),
        )
    }

    private fun createConfiguration(properties: Map<String, String>): PluginConfiguration =
        pluginService.createPluginConfiguration(
            "Without ZGW ${UUID.randomUUID()}",
            objectMapper.valueToTree<ObjectNode>(properties),
            PLUGIN_KEY,
        )

    private fun call(
        authenticator: HttpClientAuthenticator,
        url: String,
    ): String? =
        RestClient
            .builder()
            .apply { authenticator.applyAuth(it) }
            .build()
            .get()
            .uri(url)
            .retrieve()
            .body<String>()

    companion object {
        private val ZGW_MODULE_CLASSES =
            listOf(
                "com.ritense.zakenapi.ZakenApiAuthentication",
                "com.ritense.documentenapi.DocumentenApiAuthentication",
                "com.ritense.catalogiapi.CatalogiApiAuthentication",
                "com.ritense.besluitenapi.BesluitenApiAuthentication",
                "com.ritense.notificatiesapi.NotificatiesApiAuthentication",
                "com.ritense.objectenapi.ObjectenApiAuthentication",
                "com.ritense.objecttypenapi.ObjecttypenApiAuthentication",
                "com.ritense.openzaak.plugin.token.OpenZaakPluginTokenGeneratorService",
            )
    }
}

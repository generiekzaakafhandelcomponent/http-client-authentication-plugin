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
import com.ritense.authorization.AuthorizationContext.Companion.runWithoutAuthorization
import com.ritense.objectenapi.ObjectenApiPlugin
import com.ritense.plugin.autodeployment.PluginAutoDeploymentDto
import com.ritense.plugin.domain.PluginConfiguration
import com.ritense.plugin.domain.PluginConfigurationId
import com.ritense.plugin.exception.PluginEventInvocationException
import com.ritense.plugin.repository.PluginConfigurationRepository
import com.ritense.plugin.repository.PluginDefinitionRepository
import com.ritense.plugin.service.PluginService
import com.ritense.valtimoplugins.httpclientauthentication.BaseIntegrationTest
import com.ritense.valtimoplugins.httpclientauthentication.tls.MtlsTestServer
import com.ritense.valtimoplugins.httpclientauthentication.tls.TlsFixtures
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class HttpClientAuthenticationPluginIT : BaseIntegrationTest() {
    @Autowired
    lateinit var pluginService: PluginService

    @Autowired
    lateinit var pluginDefinitionRepository: PluginDefinitionRepository

    @Autowired
    lateinit var pluginConfigurationRepository: PluginConfigurationRepository

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    @Transactional
    fun `should be selectable as authentication by every ZGW API plugin`() {
        val definition = pluginDefinitionRepository.findById(PLUGIN_KEY).orElseThrow()

        assertEquals(
            setOf(
                "http-client-authentication",
                "zaken-api-authentication",
                "documenten-api-authentication",
                "catalogi-api-authentication",
                "besluiten-api-authentication",
                "notificaties-api-authentication",
                "objecten-api-authentication",
                "objecttypen-api-authentication",
                "klantinteracties-api-authentication",
            ),
            definition.categories.map { it.key }.toSet(),
        )
    }

    @Test
    @Transactional
    fun `should call the Objecten API through a gateway that requires mutual TLS`() {
        MtlsTestServer(responseBody = objectResponse()).use { server ->
            val authentication =
                createConfiguration(
                    "Den Bosch gateway",
                    mapOf(
                        "authenticationType" to "TOKEN",
                        "authSecret" to "1e2804e1113e083fb1f8258d161a2e222c317987",
                        "clientCertificate" to TlsFixtures.CLIENT_CERTIFICATE,
                        "clientPrivateKey" to TlsFixtures.CLIENT_PRIVATE_KEY,
                        "serverCertificate" to TlsFixtures.CA_CERTIFICATE,
                    ),
                    PLUGIN_KEY,
                )
            val objectenApi =
                createConfiguration(
                    "Objecten API via gateway",
                    mapOf(
                        "url" to "${server.baseUrl}/api/v2/",
                        "authenticationPluginConfiguration" to authentication.id.id.toString(),
                    ),
                    "objectenapi",
                )
            val plugin = pluginService.createInstance(objectenApi.id) as ObjectenApiPlugin

            val result =
                runWithoutAuthorization { plugin.getObject(URI("${server.baseUrl}/api/v2/objects/$OBJECT_ID")) }

            assertEquals(UUID.fromString(OBJECT_ID), result.uuid)
            assertEquals(TlsFixtures.CLIENT_SUBJECT, server.receivedClientCertificateSubject)
            assertEquals("Token 1e2804e1113e083fb1f8258d161a2e222c317987", server.receivedAuthorization)
        }
    }

    @Test
    fun `should refuse to save a configuration whose private key does not belong to the client certificate`() {
        val title = "Mismatched key ${UUID.randomUUID()}"

        val exception =
            assertThrows<PluginEventInvocationException> {
                createConfiguration(
                    title,
                    mapOf(
                        "authenticationType" to "NONE",
                        "clientCertificate" to TlsFixtures.CLIENT_CERTIFICATE,
                        "clientPrivateKey" to TlsFixtures.OTHER_PRIVATE_KEY,
                    ),
                    PLUGIN_KEY,
                )
            }

        val causes = generateSequence(exception as Throwable) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue(
            causes.any {
                it.contains("Property 'clientPrivateKey' does not belong to the certificate in 'clientCertificate'")
            },
        )
        assertTrue(pluginConfigurationRepository.findByPluginDefinitionKey(PLUGIN_KEY).none { it.title == title })
    }

    @Test
    fun `should refuse an update that renews the client certificate without its private key`() {
        val configuration = createConfiguration("Renewal ${UUID.randomUUID()}", mtlsProperties(), PLUGIN_KEY)
        val renewedProperties =
            mtlsProperties() + mapOf("clientCertificate" to TlsFixtures.SERVER_CERTIFICATE, "clientPrivateKey" to "")

        val exception =
            assertThrows<PluginEventInvocationException> {
                pluginService.updatePluginConfiguration(
                    configuration.id,
                    configuration.title,
                    objectMapper.valueToTree(renewedProperties),
                )
            }

        assertCausedBy(
            exception,
            "Property 'clientPrivateKey' does not belong to the certificate in 'clientCertificate'",
        )
        val stored = pluginConfigurationRepository.findById(configuration.id).orElseThrow()
        assertEquals(TlsFixtures.CLIENT_CERTIFICATE, stored.properties!!["clientCertificate"].textValue())
    }

    @Test
    fun `should keep the stored private key when an update leaves it empty`() {
        val configuration = createConfiguration("Edit ${UUID.randomUUID()}", mtlsProperties(), PLUGIN_KEY)
        val editedProperties = mtlsProperties() + mapOf("authSecret" to "another-token-value", "clientPrivateKey" to "")

        pluginService.updatePluginConfiguration(
            configuration.id,
            configuration.title,
            objectMapper.valueToTree(editedProperties),
        )

        val plugin = pluginService.createInstance(configuration.id) as HttpClientAuthenticationPlugin
        assertEquals(TlsFixtures.CLIENT_PRIVATE_KEY, plugin.clientPrivateKey)
        plugin.validateTlsConfiguration()
    }

    @Test
    fun `should refuse to autodeploy a configuration whose private key does not belong to the client certificate`() {
        val id = UUID.randomUUID()
        val properties = mtlsProperties() + mapOf("clientPrivateKey" to TlsFixtures.OTHER_PRIVATE_KEY)

        val exception =
            assertThrows<PluginEventInvocationException> {
                pluginService.deployPluginConfigurations(
                    PluginAutoDeploymentDto(
                        id,
                        "Autodeployed ${UUID.randomUUID()}",
                        objectMapper.valueToTree(properties),
                        PLUGIN_KEY,
                    ),
                )
            }

        assertCausedBy(
            exception,
            "Property 'clientPrivateKey' does not belong to the certificate in 'clientCertificate'",
        )
        assertFalse(pluginConfigurationRepository.existsById(PluginConfigurationId.existingId(id)))
    }

    private fun assertCausedBy(
        exception: Throwable,
        message: String,
    ) {
        val messages = generateSequence(exception) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue(messages.any { it.contains(message) }, messages.joinToString())
    }

    private fun mtlsProperties() =
        mapOf(
            "authenticationType" to "TOKEN",
            "authSecret" to "1e2804e1113e083fb1f8258d161a2e222c317987",
            "clientCertificate" to TlsFixtures.CLIENT_CERTIFICATE,
            "clientPrivateKey" to TlsFixtures.CLIENT_PRIVATE_KEY,
            "serverCertificate" to TlsFixtures.CA_CERTIFICATE,
        )

    private fun createConfiguration(
        title: String,
        properties: Map<String, String>,
        pluginDefinitionKey: String,
    ): PluginConfiguration =
        pluginService.createPluginConfiguration(
            title,
            objectMapper.valueToTree<ObjectNode>(properties),
            pluginDefinitionKey,
        )

    private fun objectResponse() =
        """
        {
          "url": "https://example.com/api/v2/objects/$OBJECT_ID",
          "uuid": "$OBJECT_ID",
          "type": "https://example.com/api/v2/objecttypes/55a89742-6ae0-49bb-a00e-6465b13cfc07",
          "record": { "typeVersion": 1, "data": {}, "startAt": "2026-10-20" }
        }
        """.trimIndent()

    companion object {
        private const val PLUGIN_KEY = "http-client-authentication-plugin"
        private const val OBJECT_ID = "1e9b6d4c-5b1a-4b3e-9f7a-2c8d0e6f4a11"
    }
}

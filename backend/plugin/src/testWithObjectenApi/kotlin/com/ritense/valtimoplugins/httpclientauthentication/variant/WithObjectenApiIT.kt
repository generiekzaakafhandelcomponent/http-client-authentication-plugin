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
import com.ritense.authorization.AuthorizationContext.Companion.runWithoutAuthorization
import com.ritense.objectenapi.ObjectenApiPlugin
import com.ritense.plugin.domain.PluginConfiguration
import com.ritense.plugin.repository.PluginDefinitionRepository
import com.ritense.plugin.service.PluginService
import com.ritense.valtimoplugins.httpclientauthentication.BaseIntegrationTest
import com.ritense.valtimoplugins.httpclientauthentication.plugin.HttpClientAuthenticationPluginBase.Companion.PLUGIN_KEY
import com.ritense.valtimoplugins.httpclientauthentication.tls.MtlsTestServer
import com.ritense.valtimoplugins.httpclientauthentication.tls.TlsFixtures
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.ClassUtils
import java.net.URI
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// Runs on the classpath of an application that adds the Objecten and Objecttypen API modules only. Objecten API
// brings Zaken API along, which brings Documenten and Catalogi API.
internal class WithObjectenApiIT : BaseIntegrationTest() {
    @Autowired
    lateinit var pluginService: PluginService

    @Autowired
    lateinit var pluginDefinitionRepository: PluginDefinitionRepository

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `should run without the other ZGW modules`() {
        listOf(
            "com.ritense.besluitenapi.BesluitenApiAuthentication",
            "com.ritense.notificatiesapi.NotificatiesApiAuthentication",
            "com.ritense.klantinteractiesapi.KlantinteractiesApiAuthentication",
        ).forEach { assertFalse(ClassUtils.isPresent(it, null), it) }
    }

    @Test
    @Transactional
    fun `should deploy the variant for the ZGW modules on the classpath`() {
        val definition = pluginDefinitionRepository.findById(PLUGIN_KEY).orElseThrow()

        assertEquals(
            "valtimoplugins.httpclientauthentication.variant.z11100110.HttpClientAuthenticationPlugin",
            definition.fullyQualifiedClassName,
        )
        assertEquals(
            setOf(
                "http-client-authentication",
                "zaken-api-authentication",
                "documenten-api-authentication",
                "catalogi-api-authentication",
                "objecten-api-authentication",
                "objecttypen-api-authentication",
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

    private fun createConfiguration(
        properties: Map<String, String>,
        pluginDefinitionKey: String,
    ): PluginConfiguration =
        pluginService.createPluginConfiguration(
            "With Objecten API ${UUID.randomUUID()}",
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
        private const val OBJECT_ID = "1e9b6d4c-5b1a-4b3e-9f7a-2c8d0e6f4a11"
    }
}

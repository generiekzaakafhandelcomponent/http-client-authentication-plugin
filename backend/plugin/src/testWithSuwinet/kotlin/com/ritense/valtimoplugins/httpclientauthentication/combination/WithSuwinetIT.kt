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

package com.ritense.valtimoplugins.httpclientauthentication.combination

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.ritense.plugin.repository.PluginDefinitionRepository
import com.ritense.plugin.service.PluginService
import com.ritense.valtimoplugins.httpclientauthentication.BaseIntegrationTest
import com.ritense.valtimoplugins.httpclientauthentication.cxf.soapClient
import com.ritense.valtimoplugins.httpclientauthentication.plugin.GeneratedPluginClass
import com.ritense.valtimoplugins.httpclientauthentication.plugin.HttpClientAuthenticationPluginBase.Companion.PLUGIN_KEY
import com.ritense.valtimoplugins.httpclientauthentication.tls.MtlsTestServer
import com.ritense.valtimoplugins.httpclientauthentication.tls.TlsFixtures
import com.ritense.valtimoplugins.suwinetauth.plugin.SuwinetAuth
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.ClassUtils
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// Runs on the classpath of an application with Suwinet (SOAP) only: no ZGW module, no other covered plugin
internal class WithSuwinetIT : BaseIntegrationTest() {
    @Autowired
    lateinit var pluginService: PluginService

    @Autowired
    lateinit var pluginDefinitionRepository: PluginDefinitionRepository

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    @Transactional
    fun `should be selectable as Suwinet authentication only`() {
        assertFalse(ClassUtils.isPresent("com.ritense.zakenapi.ZakenApiAuthentication", null))
        assertFalse(
            ClassUtils.isPresent("com.ritense.valtimoplugins.haalcentraalauth.HaalCentraalAuthentication", null),
        )
        val definition = pluginDefinitionRepository.findById(PLUGIN_KEY).orElseThrow()

        assertEquals(GeneratedPluginClass.CLASS_NAME, definition.fullyQualifiedClassName)
        assertEquals(
            setOf("http-client-authentication", "suwinet-authentication"),
            definition.categories.map { it.key }.toSet(),
        )
    }

    @Test
    @Transactional
    fun `should authenticate a Suwinet SOAP call with header and client certificate`() {
        MtlsTestServer().use { server ->
            val configuration =
                pluginService.createPluginConfiguration(
                    "Suwinet ${UUID.randomUUID()}",
                    objectMapper.valueToTree<ObjectNode>(
                        mapOf(
                            "authenticationType" to "HEADER",
                            "authHeaderName" to "x-opentunnel-api-key",
                            "authSecret" to "tunnel-key",
                            "clientCertificate" to TlsFixtures.CLIENT_CERTIFICATE,
                            "clientPrivateKey" to TlsFixtures.CLIENT_PRIVATE_KEY,
                            "serverCertificate" to TlsFixtures.CA_CERTIFICATE,
                        ),
                    ),
                    PLUGIN_KEY,
                )
            val (service, client) = soapClient(server.baseUrl)

            (pluginService.createInstance(configuration.id) as SuwinetAuth).applyAuth(client)
            service.ping("hello")

            val request = server.receivedRequests.single()
            assertEquals("tunnel-key", request.header("x-opentunnel-api-key"))
            assertEquals(TlsFixtures.CLIENT_SUBJECT, request.clientCertificateSubject)
        }
    }
}

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

package com.ritense.valtimoplugins.httpclientauthentication.cxf

import com.ritense.valtimo.contract.client.ValtimoHttpRestClientConfigurationProperties
import com.ritense.valtimoplugins.httpclientauthentication.model.AuthenticationType
import com.ritense.valtimoplugins.httpclientauthentication.plugin.configuredPlugin
import com.ritense.valtimoplugins.httpclientauthentication.plugin.configuredPluginWithCertificates
import com.ritense.valtimoplugins.httpclientauthentication.tls.MtlsTestServer
import com.ritense.valtimoplugins.httpclientauthentication.tls.TlsFixtures
import com.ritense.valtimoplugins.httpclientauthentication.tls.TlsRequestFactoryCache
import com.ritense.valtimoplugins.suwinetauth.plugin.SuwinetAuth
import org.apache.cxf.configuration.jsse.TLSClientParameters
import org.apache.cxf.message.Message
import org.apache.cxf.message.MessageImpl
import org.apache.cxf.transport.http.HTTPConduit
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

// Real CXF (SOAP) clients, see soapClient
internal class CxfAuthenticatorTest {
    @Test
    fun `should send the headers and present the client certificate on a SOAP call`() {
        MtlsTestServer().use { server ->
            val plugin =
                configuredPluginWithCertificates(AuthenticationType.BEARER_AND_API_KEY) {
                    authSecret =
                        "api-key"
                }
            val (service, client) = soapClient(server.baseUrl)

            (plugin as SuwinetAuth).applyAuth(client)
            service.ping("hello")

            val request = server.receivedRequests.single()
            assertEquals(TlsFixtures.CLIENT_SUBJECT, request.clientCertificateSubject)
            assertEquals("Bearer api-key", request.header("Authorization"))
            assertEquals("api-key", request.header("X-Api-Key"))
            assertTrue(request.body.contains("hello"))
        }
    }

    @Test
    fun `should send Basic credentials on a SOAP call, like suwinet-auth's BASIC`() {
        MtlsTestServer().use { server ->
            val plugin =
                configuredPluginWithCertificates(AuthenticationType.BASIC) {
                    username = "suwinet"
                    authSecret = "password"
                }
            val (service, client) = soapClient(server.baseUrl)

            (plugin as SuwinetAuth).applyAuth(client)
            service.ping("hello")

            assertEquals("Basic c3V3aW5ldDpwYXNzd29yZA==", server.receivedAuthorization)
        }
    }

    @Test
    fun `should keep the consumer's timeouts and TLS settings, changing a copy of the TLS parameters`() {
        val (_, client) = soapClient("https://suwinet.example.com")
        val conduit = client.conduit as HTTPConduit
        val policy = conduit.client
        val consumerParameters = TLSClientParameters().apply { isDisableCNCheck = true }
        conduit.tlsClientParameters = consumerParameters

        (configuredPluginWithCertificates(AuthenticationType.NONE) as SuwinetAuth).applyAuth(client)

        assertSame(policy, conduit.client)
        assertNotSame(consumerParameters, conduit.tlsClientParameters)
        assertTrue(conduit.tlsClientParameters.isDisableCNCheck)
        assertEquals(1, conduit.tlsClientParameters.keyManagers.size)
        assertNull(consumerParameters.keyManagers)
    }

    @Test
    fun `should keep the headers already on the message, also when they are immutable`() {
        val plugin = configuredPlugin(AuthenticationType.BEARER) { authSecret = "token" }
        val message = MessageImpl().apply { put(Message.PROTOCOL_HEADERS, mapOf("Expect" to listOf("100-continue"))) }

        CxfAuthenticator.AuthenticationHeaderInterceptor(plugin).handleMessage(message)

        @Suppress("UNCHECKED_CAST")
        val headers = message[Message.PROTOCOL_HEADERS] as Map<String, List<String>>
        assertEquals(listOf("100-continue"), headers["expect"])
        assertEquals(listOf("Bearer token"), headers["Authorization"])
    }

    @Test
    fun `should add one header interceptor when applied twice, and none for NONE`() {
        val (_, client) = soapClient("https://suwinet.example.com")
        val bearer = configuredPlugin(AuthenticationType.BEARER) { authSecret = "token" } as SuwinetAuth

        bearer.applyAuth(client)
        bearer.applyAuth(client)
        assertEquals(1, client.outInterceptors.count { it is CxfAuthenticator.AuthenticationHeaderInterceptor })

        val (_, otherClient) = soapClient("https://suwinet.example.com")
        (configuredPlugin(AuthenticationType.NONE) as SuwinetAuth).applyAuth(otherClient)
        assertEquals(0, otherClient.outInterceptors.count { it is CxfAuthenticator.AuthenticationHeaderInterceptor })
    }

    @Test
    fun `should not put the certificates of SAML_TOKEN on the conduit`() {
        val (_, client) = soapClient("https://suwinet.example.com")
        val plugin =
            configuredPluginWithCertificates(AuthenticationType.SAML_TOKEN) {
                tokenEndpoint = java.net.URI("https://sts.example.com/sts")
            }

        (plugin as SuwinetAuth).applyAuth(client)

        assertNull((client.conduit as HTTPConduit).tlsClientParameters)
    }

    @Test
    fun `should share the key managers between clients of the same configuration`() {
        val (_, first) = soapClient("https://suwinet.example.com")
        val (_, second) = soapClient("https://suwinet.example.com")

        // One cache bean in the application, plugin instances per use
        val cache = TlsRequestFactoryCache(ValtimoHttpRestClientConfigurationProperties())

        (configuredPluginWithCertificates(AuthenticationType.NONE, cache) as SuwinetAuth).applyAuth(first)
        (configuredPluginWithCertificates(AuthenticationType.NONE, cache) as SuwinetAuth).applyAuth(second)

        assertEquals(
            (first.conduit as HTTPConduit).tlsClientParameters,
            (second.conduit as HTTPConduit).tlsClientParameters,
        )
    }
}

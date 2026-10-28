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

import com.ritense.tokenauthentication.plugin.TokenAuthentication
import com.ritense.valtimoplugins.haalcentraalauth.HaalCentraalAuthentication
import com.ritense.valtimoplugins.httpclientauthentication.model.AuthenticationType
import com.ritense.valtimoplugins.httpclientauthentication.tls.MtlsTestServer
import com.ritense.valtimoplugins.httpclientauthentication.tls.TlsFixtures
import com.ritense.valtimoplugins.mtlssslcontext.MTlsSslContext
import com.ritense.valtimoplugins.tokenexchangeauth.TokenExchangeAuthentication
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull
import com.ritense.valtimoplugins.haalcentraalauthenticationplugin.HaalCentraalAuthentication as HaalCentraalSamlAuthentication

// What callers of each covered interface get from this plugin
internal class CoveredInterfacesBehaviourTest {
    @Test
    fun `should authenticate a RestClient for TokenAuthentication callers, like tokenauthentication`() {
        MtlsTestServer().use { server ->
            val plugin =
                configuredPluginWithCertificates(
                    AuthenticationType.TOKEN,
                ) { authSecret = "token" } as TokenAuthentication

            plugin
                .applyAuth(RestClient.builder())
                .build()
                .get()
                .uri(server.baseUrl)
                .retrieve()
                .body<String>()

            assertEquals("Token token", server.receivedAuthorization)
        }
    }

    @Test
    fun `should return a reactor-netty client with the certificates and headers for both Haal Centraal interfaces`() {
        MtlsTestServer(responseBody = "ok").use { server ->
            val plugin =
                configuredPluginWithCertificates(AuthenticationType.BEARER_AND_API_KEY) {
                    authSecret =
                        "api-key"
                }

            listOf(
                (plugin as HaalCentraalAuthentication).getAuthenticatedHttpClient(),
                (plugin as HaalCentraalSamlAuthentication).getAuthenticatedHttpClient(),
            ).forEach { client ->
                val body =
                    client
                        .get()
                        .uri(server.baseUrl)
                        .responseContent()
                        .aggregate()
                        .asString()
                        .block()

                assertEquals("ok", body)
                val request = server.receivedRequests.last()
                assertEquals(TlsFixtures.CLIENT_SUBJECT, request.clientCertificateSubject)
                assertEquals("Bearer api-key", request.header("Authorization"))
                assertEquals("api-key", request.header("X-Api-Key"))
            }
        }
    }

    @Test
    fun `should give TokenExchangeAuthentication callers the bearer token and the SSL context`() {
        MtlsTestServer(responseBody = "ok").use { server ->
            val plugin =
                configuredPluginWithCertificates(
                    AuthenticationType.BEARER,
                ) { authSecret = "bearer-token" } as TokenExchangeAuthentication

            assertEquals("bearer-token", plugin.getAccessToken())
            assertEquals("ok", get(server, plugin.getSslContext()!!))
            assertEquals(TlsFixtures.CLIENT_SUBJECT, server.receivedClientCertificateSubject)
        }
    }

    @Test
    fun `should refuse a token to TokenExchangeAuthentication callers for a type without bearer token`() {
        val plugin =
            configuredPlugin(AuthenticationType.HEADER) {
                authHeaderName = "X-Key"
                authSecret = "key"
            } as TokenExchangeAuthentication

        val exception = assertThrows<IllegalStateException> { plugin.getAccessToken() }

        assertEquals("Authentication type HEADER has no bearer token", exception.message)
        assertNull(plugin.getSslContext())
    }

    @Test
    fun `should give MTlsSslContext callers the SSL context, and refuse without certificates`() {
        MtlsTestServer(responseBody = "ok").use { server ->
            val plugin = configuredPluginWithCertificates(AuthenticationType.NONE) as MTlsSslContext

            assertEquals("ok", get(server, plugin.createSslContext()))
        }
        val exception =
            assertThrows<IllegalStateException> {
                (configuredPlugin(AuthenticationType.NONE) as MTlsSslContext).createSslContext()
            }
        assertEquals("No certificates are configured", exception.message)
    }

    @Test
    fun `should keep the certificates of SAML_TOKEN for the token service on every path`() {
        val plugin =
            configuredPluginWithCertificates(AuthenticationType.SAML_TOKEN) {
                tokenEndpoint =
                    URI("https://sts.example.com/sts")
            }

        assertNull((plugin as TokenExchangeAuthentication).getSslContext())
        val exception = assertThrows<IllegalStateException> { (plugin as MTlsSslContext).createSslContext() }
        assertEquals(
            "Authentication type SAML_TOKEN uses its certificates for the token service only",
            exception.message,
        )
        assertNull(plugin.apiTlsMaterial())
    }

    private fun get(
        server: MtlsTestServer,
        sslContext: javax.net.ssl.SSLContext,
    ): String =
        HttpClient
            .newBuilder()
            .sslContext(sslContext)
            .connectTimeout(Duration.ofSeconds(5))
            .build()
            .send(HttpRequest.newBuilder(URI(server.baseUrl)).build(), HttpResponse.BodyHandlers.ofString())
            .body()
}

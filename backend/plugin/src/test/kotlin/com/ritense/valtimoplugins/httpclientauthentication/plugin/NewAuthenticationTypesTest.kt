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

import com.ritense.valtimo.contract.client.ValtimoHttpRestClientConfigurationProperties
import com.ritense.valtimoplugins.httpclientauthentication.exception.SamlTokenException
import com.ritense.valtimoplugins.httpclientauthentication.model.AuthenticationType
import com.ritense.valtimoplugins.httpclientauthentication.saml.SamlTokenClient
import com.ritense.valtimoplugins.httpclientauthentication.tls.MtlsTestServer
import com.ritense.valtimoplugins.httpclientauthentication.tls.TlsFixtures
import com.ritense.valtimoplugins.httpclientauthentication.tls.TlsRequestFactoryCache
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Answers
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.boot.ssl.pem.PemContent
import org.springframework.boot.ssl.pem.PemSslStore
import org.springframework.boot.ssl.pem.PemSslStoreBundle
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.net.URI
import java.util.Base64
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class NewAuthenticationTypesTest {
    @Test
    fun `should send username and password as Basic credentials in UTF-8 for BASIC`() {
        val headers =
            headersOf(
                configuredPlugin(AuthenticationType.BASIC) {
                    username = "valtimo"
                    authSecret = "wachtwoörd"
                },
            )

        val credentials = Base64.getEncoder().encodeToString("valtimo:wachtwoörd".toByteArray(Charsets.UTF_8))
        assertEquals("Basic $credentials", headers.getFirst(HttpHeaders.AUTHORIZATION))
    }

    @Test
    fun `should require username and password for BASIC, and refuse a colon in the username`() {
        val missing =
            assertThrows<IllegalArgumentException> {
                configuredPlugin(
                    AuthenticationType.BASIC,
                ).validateConfiguration()
            }
        val colon =
            assertThrows<IllegalArgumentException> {
                configuredPlugin(AuthenticationType.BASIC) {
                    username = "a:b"
                    authSecret = "secret"
                }.validateConfiguration()
            }

        assertEquals("Authentication type BASIC requires the properties: username, authSecret", missing.message)
        assertEquals("Property 'username' must not contain ':'", colon.message)
    }

    @Test
    fun `should send the secret as bearer token and X-Api-Key for BEARER_AND_API_KEY, like haal-centraal-auth`() {
        val headers = headersOf(configuredPlugin(AuthenticationType.BEARER_AND_API_KEY) { authSecret = "api-key" })

        assertEquals("Bearer api-key", headers.getFirst(HttpHeaders.AUTHORIZATION))
        assertEquals("api-key", headers.getFirst("X-Api-Key"))
    }

    @Test
    fun `should refuse a secret that can't be sent in a header, without echoing it`() {
        listOf("s3c\nr3t", "s3c\u0000r3t", "s3c\u0085r3t", "s3cr3t€").forEach { secret ->
            val exception =
                assertThrows<IllegalArgumentException> {
                    configuredPlugin(AuthenticationType.BEARER) { authSecret = secret }.validateConfiguration()
                }

            assertEquals(
                "Property 'authSecret' contains a character that can't be sent in a header " +
                    "(a control character or one above U+00FF)",
                exception.message,
            )
            assertFalse(exception.message!!.contains("r3t"))
        }
        configuredPlugin(AuthenticationType.BEARER) { authSecret = "s3cr3t é\t" }.validateConfiguration()
    }

    @Test
    fun `should keep accepting a secret with surrounding line breaks, as 3_0_0 did, and send it trimmed`() {
        val types = listOf(AuthenticationType.BEARER, AuthenticationType.TOKEN, AuthenticationType.BEARER_AND_API_KEY)
        types.forEach { type ->
            val plugin = configuredPlugin(type) { authSecret = "\r\n s3cr3t\n" }

            plugin.validateConfiguration()
            val headers = headersOf(plugin)

            assertTrue(headers.getFirst(HttpHeaders.AUTHORIZATION)!!.endsWith(" s3cr3t"), type.name)
        }
        val header =
            configuredPlugin(AuthenticationType.HEADER) {
                authHeaderName = "X-Api-Key"
                authSecret = "s3cr3t\n"
            }
        assertEquals("s3cr3t", headersOf(header).getFirst("X-Api-Key"))
    }

    @Test
    fun `should send the SAML token as bearer token, presenting the client certificate only to the token service`() {
        MtlsTestServer(responseBody = "  c2FtbC10b2tlbg==\n").use { tokenService ->
            val plugin = samlPlugin(tokenService)
            val builder = mock<RestClient.Builder>(defaultAnswer = Answers.RETURNS_SELF)

            plugin.validateConfiguration()
            plugin.applyAuth(builder)
            val headers = headersOf(plugin)

            assertEquals("Bearer c2FtbC10b2tlbg==", headers.getFirst(HttpHeaders.AUTHORIZATION))
            // The API isn't called with the token service's certificates
            verify(builder, never()).requestFactory(any())
            val request = tokenService.receivedRequests.single()
            assertEquals("POST /sts/requestsecuritytoken/v1", request.request)
            assertEquals(TlsFixtures.CLIENT_SUBJECT, request.clientCertificateSubject)
            assertEquals("http://docs.oasis-open.org/ws-sx/ws-trust/200512/RST/Issue", request.header("SOAPAction"))
            assertTrue(request.header("Content-Type")!!.startsWith("text/xml"))
            assertTrue(request.body.contains("<ns:TokenType>urn:oasis:names:tc:SAML:2.0:assertion</ns:TokenType>"))
        }
    }

    @Test
    fun `should send the SAML token in the configured header`() {
        MtlsTestServer(responseBody = "c2FtbC10b2tlbg==").use { tokenService ->
            val headers = headersOf(samlPlugin(tokenService) { authHeaderName = "x-saml-attribute-token1" })

            assertEquals("c2FtbC10b2tlbg==", headers.getFirst("x-saml-attribute-token1"))
            assertNull(headers.getFirst(HttpHeaders.AUTHORIZATION))
        }
    }

    @Test
    fun `should fetch the SAML token off the WebClient's thread`() {
        var tokenThread: String? = null
        val samlTokenClient =
            mock<SamlTokenClient> {
                on { token(any(), any()) } doAnswer {
                    tokenThread = Thread.currentThread().name
                    "c2FtbC10b2tlbg=="
                }
            }
        val plugin =
            newPlugin(samlTokenClient = samlTokenClient).also {
                it.authenticationType = AuthenticationType.SAML_TOKEN
                it.tokenEndpoint = URI("https://sts.example.com/sts?b64=true")
            }
        var sentAuthorization: String? = null

        WebClient
            .builder()
            .filter(plugin)
            .exchangeFunction { request ->
                sentAuthorization = request.headers().getFirst(HttpHeaders.AUTHORIZATION)
                Mono.just(ClientResponse.create(HttpStatus.OK).build())
            }.build()
            .get()
            .uri(RESOURCE_URL)
            .retrieve()
            .toBodilessEntity()
            .block()

        assertEquals("Bearer c2FtbC10b2tlbg==", sentAuthorization)
        assertTrue(tokenThread!!.startsWith("boundedElastic"), tokenThread)
    }

    @Test
    fun `should trust only the configured server certificate for the SAML token service`() {
        MtlsTestServer(responseBody = "c2FtbC10b2tlbg==").use { tokenService ->
            // The JVM's trust here is the test CA, which signed the token service's certificate; the configured server
            // certificate didn't
            val jvmTrust =
                TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm())
                    .apply {
                        init(
                            PemSslStoreBundle(
                                null,
                                PemSslStore.of(PemContent.of(TlsFixtures.CA_CERTIFICATE).certificates, null),
                            ).trustStore,
                        )
                    }.trustManagers
                    .filterIsInstance<X509ExtendedTrustManager>()
                    .first()
            val cache = TlsRequestFactoryCache(ValtimoHttpRestClientConfigurationProperties(), jvmTrust)
            val plugin =
                configuredPluginWithCertificates(AuthenticationType.SAML_TOKEN, cache) {
                    tokenEndpoint = URI("${tokenService.baseUrl}/sts?b64=true")
                    serverCertificate = TlsFixtures.CLIENT_CERTIFICATE
                }

            assertThrows<SamlTokenException> { plugin.bearerToken() }
            assertTrue(tokenService.receivedRequests.isEmpty())
        }
    }

    @Test
    fun `should refuse an invalid header name for the SAML token`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                configuredPlugin(AuthenticationType.SAML_TOKEN) {
                    tokenEndpoint = URI("https://sts.example.com/sts")
                    authHeaderName = "Bad Header: Name"
                }.validateConfiguration()
            }

        assertEquals("Property 'authHeaderName' is not a valid header name", exception.message)
    }

    @Test
    fun `should refuse a SAML response that is not a single-line token, without echoing it`() {
        MtlsTestServer(responseBody = "<saml:Assertion>\nsecret-assertion\n</saml:Assertion>").use { tokenService ->
            val exception = assertThrows<SamlTokenException> { headersOf(samlPlugin(tokenService)) }

            assertTrue(exception.message!!.contains("did not return a single-line token"), exception.message)
            assertFalse(exception.message!!.contains("secret-assertion"))
        }
    }

    @Test
    fun `should give up on a slow SAML token service after the configured response timeout`() {
        MtlsTestServer(responseBody = "c2FtbC10b2tlbg==", responseDelayMillis = 2_000).use { tokenService ->
            val plugin = samlPlugin(tokenService) { responseTimeout = 300 }

            val exception = assertThrows<SamlTokenException> { plugin.bearerToken() }

            assertTrue(exception.message!!.startsWith("Could not reach the SAML token service"), exception.message)
        }
    }

    @Test
    fun `should require an https token service URL and positive timeouts for SAML_TOKEN`() {
        val http =
            assertThrows<IllegalArgumentException> {
                configuredPlugin(AuthenticationType.SAML_TOKEN) { tokenEndpoint = URI("http://sts.example.com/sts") }
                    .validateConfiguration()
            }
        val timeout =
            assertThrows<IllegalArgumentException> {
                configuredPlugin(AuthenticationType.SAML_TOKEN) {
                    tokenEndpoint = URI("https://sts.example.com/sts")
                    connectionTimeout = 0
                }.validateConfiguration()
            }

        assertEquals("Property 'tokenEndpoint' must be an https URL for authentication type SAML_TOKEN", http.message)
        assertEquals("Property 'connectionTimeout' must be a positive number of milliseconds", timeout.message)
    }

    private fun samlPlugin(
        tokenService: MtlsTestServer,
        configure: HttpClientAuthenticationPluginBase.() -> Unit = {},
    ) = configuredPluginWithCertificates(AuthenticationType.SAML_TOKEN) {
        tokenEndpoint = URI("${tokenService.baseUrl}/sts/requestsecuritytoken/v1?b64=true")
        configure()
    }

    private fun headersOf(plugin: HttpClientAuthenticationPluginBase): HttpHeaders {
        val builder = RestClient.builder()
        plugin.applyAuth(builder)
        val sent = HttpHeaders()
        val server = MockRestServiceServer.bindTo(builder).build()
        server
            .expect(requestTo(RESOURCE_URL))
            .andExpect { request -> sent.putAll(request.headers) }
            .andRespond(withSuccess())
        builder
            .build()
            .get()
            .uri(RESOURCE_URL)
            .retrieve()
            .toBodilessEntity()
        server.verify()
        return sent
    }

    companion object {
        private const val RESOURCE_URL = "https://api.example.com/resource"
    }
}

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
import com.ritense.plugin.domain.PluginConfigurationId
import com.ritense.valtimo.contract.client.HostDockerInternalRestClientCustomizer
import com.ritense.valtimo.contract.client.ValtimoHttpRestClientConfigurationProperties
import com.ritense.valtimoplugins.httpclientauthentication.model.AuthenticationType
import com.ritense.valtimoplugins.httpclientauthentication.tls.MtlsTestServer
import com.ritense.valtimoplugins.httpclientauthentication.tls.TlsFixtures
import com.ritense.valtimoplugins.httpclientauthentication.tls.TlsRequestFactoryCache
import com.ritense.valtimoplugins.httpclientauthentication.tokenexchange.TokenExchangeClient
import com.ritense.valtimoplugins.httpclientauthentication.tokenexchange.TokenExchangeConfig
import com.ritense.valtimoplugins.httpclientauthentication.tokenexchange.TokenExchangeTokenCache
import com.ritense.valtimoplugins.httpclientauthentication.tokenexchange.TokenExchangeTokenResponse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.Answers
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.ssl.pem.PemContent
import org.springframework.boot.ssl.pem.PemSslStore
import org.springframework.boot.ssl.pem.PemSslStoreBundle
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import valtimoplugins.httpclientauthentication.variant.z0000000.HttpClientAuthenticationPlugin
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal class HttpClientAuthenticationPluginTest {
    private val restClientProperties = ValtimoHttpRestClientConfigurationProperties()

    private val tokenExchangeClient =
        mock<TokenExchangeClient> {
            on { exchangeToken(any(), anyOrNull()) } doReturn TokenExchangeTokenResponse("exchanged-jwt", 300)
        }

    @TempDir
    lateinit var temporaryDirectory: Path

    // Built the way Valtimo's shared RestClient.Builder is, and used per request the way the ZGW clients use it
    private val sharedRestClientBuilder =
        RestClient
            .builder()
            .requestFactory(BufferingClientHttpRequestFactory(HttpComponentsClientHttpRequestFactory()))

    @Test
    fun `should send bearer token`() {
        val plugin = plugin(AuthenticationType.BEARER) { authSecret = "secret-bearer-token" }

        assertEquals("Bearer secret-bearer-token", headersOf(plugin).getFirst(HttpHeaders.AUTHORIZATION))
    }

    @Test
    fun `should send custom header`() {
        val plugin =
            plugin(AuthenticationType.HEADER) {
                authHeaderName = "X-Api-Key"
                authSecret = "secret-api-key"
            }

        val headers = headersOf(plugin)

        assertEquals("secret-api-key", headers.getFirst("X-Api-Key"))
        assertNull(headers.getFirst(HttpHeaders.AUTHORIZATION))
    }

    @Test
    fun `should send no authentication header for NONE`() {
        val headers = headersOf(plugin(AuthenticationType.NONE))

        assertTrue(headers.isEmpty())
    }

    @Test
    fun `should send Authorization Token header for TOKEN`() {
        val plugin = plugin(AuthenticationType.TOKEN) { authSecret = "1e2804e1113e083fb1f8258d161a2e222c317987" }

        assertEquals(
            "Token 1e2804e1113e083fb1f8258d161a2e222c317987",
            headersOf(plugin).getFirst(HttpHeaders.AUTHORIZATION),
        )
    }

    @Test
    fun `should send ZGW JWT signed with the client secret for ZGW_JWT`() {
        val clientSecret = "a-client-secret-of-at-least-32-characters"
        val plugin =
            plugin(AuthenticationType.ZGW_JWT) {
                clientId = "valtimo_client"
                authSecret = clientSecret
            }

        val authorization = headersOf(plugin).getFirst(HttpHeaders.AUTHORIZATION)!!

        assertTrue(authorization.startsWith("Bearer "))
        val (header, payload, signature) = authorization.removePrefix("Bearer ").split(".")
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(clientSecret.toByteArray(), "HmacSHA256")) }
        val expectedSignature =
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal("$header.$payload".toByteArray()),
            )
        assertEquals(expectedSignature, signature)
        val claims = ObjectMapper().readTree(Base64.getUrlDecoder().decode(payload))
        assertEquals("valtimo_client", claims["client_id"].asText())
        assertEquals("valtimo_client", claims["iss"].asText())
        assertEquals("Valtimo", claims["user_id"].asText())
    }

    @Test
    fun `should require client id for ZGW_JWT`() {
        val plugin =
            plugin(AuthenticationType.ZGW_JWT) { authSecret = "a-client-secret-of-at-least-32-characters" }

        val exception = assertThrows<IllegalArgumentException> { headersOf(plugin) }

        assertEquals("Authentication type ZGW_JWT requires the properties: clientId", exception.message)
    }

    @Test
    fun `should require auth secret for TOKEN`() {
        val plugin = plugin(AuthenticationType.TOKEN)

        val exception = assertThrows<IllegalArgumentException> { plugin.validateConfiguration() }

        assertEquals("Authentication type TOKEN requires the properties: authSecret", exception.message)
    }

    @Test
    fun `should refuse a client secret shorter than 32 characters for ZGW_JWT`() {
        val plugin =
            plugin(AuthenticationType.ZGW_JWT) {
                clientId = "valtimo_client"
                authSecret = "shorter-than-32-characters"
            }

        val exception = assertThrows<IllegalArgumentException> { plugin.validateConfiguration() }

        assertEquals(
            "Property 'authSecret' must be at least 32 characters for authentication type ZGW_JWT",
            exception.message,
        )
    }

    @Test
    fun `should apply the same headers through the WebClient filter`() {
        val plugin = plugin(AuthenticationType.TOKEN) { authSecret = "secret-token" }
        var sentAuthorization: String? = null

        WebClient
            .builder()
            .filter(plugin)
            .exchangeFunction { request ->
                sentAuthorization = request.headers().getFirst(HttpHeaders.AUTHORIZATION)
                Mono.just(
                    ClientResponse.create(HttpStatus.OK).build(),
                )
            }.build()
            .get()
            .uri("http://localhost/")
            .retrieve()
            .toBodilessEntity()
            .block()

        assertEquals("Token secret-token", sentAuthorization)
    }

    @Test
    fun `should not replace the request factory without certificates`() {
        listOf(AuthenticationType.NONE, AuthenticationType.BEARER, AuthenticationType.HEADER).forEach { type ->
            val plugin =
                plugin(type) {
                    authHeaderName = "X-Api-Key"
                    authSecret = "secret"
                }
            val builder = mock<RestClient.Builder>(defaultAnswer = Answers.RETURNS_SELF)

            plugin.applyAuth(builder)

            verify(builder, never()).requestFactory(any())
        }
    }

    @Test
    fun `should ignore a stored private key without client certificate`() {
        val plugin = plugin(AuthenticationType.NONE) { clientPrivateKey = TlsFixtures.CLIENT_PRIVATE_KEY }
        val builder = mock<RestClient.Builder>(defaultAnswer = Answers.RETURNS_SELF)

        plugin.validateConfiguration()
        plugin.applyAuth(builder)

        verify(builder, never()).requestFactory(any())
    }

    @Test
    fun `should present client certificate and token to a server that requires mutual TLS`() {
        MtlsTestServer().use { server ->
            val plugin =
                plugin(AuthenticationType.TOKEN) {
                    authSecret = "1e2804e1113e083fb1f8258d161a2e222c317987"
                    clientCertificate = TlsFixtures.CLIENT_CERTIFICATE
                    clientPrivateKey = TlsFixtures.CLIENT_PRIVATE_KEY
                    serverCertificate = TlsFixtures.CA_CERTIFICATE
                }

            val body = call(plugin, server.baseUrl)

            assertEquals("{}", body)
            assertEquals(TlsFixtures.CLIENT_SUBJECT, server.receivedClientCertificateSubject)
            assertEquals("Token 1e2804e1113e083fb1f8258d161a2e222c317987", server.receivedAuthorization)
        }
    }

    @Test
    fun `should read a PKCS1 private key`() {
        MtlsTestServer().use { server ->
            val plugin =
                plugin(AuthenticationType.NONE) {
                    clientCertificate = TlsFixtures.CLIENT_CERTIFICATE
                    clientPrivateKey = TlsFixtures.CLIENT_PRIVATE_KEY_PKCS1
                    serverCertificate = TlsFixtures.CA_CERTIFICATE
                }

            call(plugin, server.baseUrl)

            assertEquals(TlsFixtures.CLIENT_SUBJECT, server.receivedClientCertificateSubject)
        }
    }

    @Test
    fun `should be rejected by a server that requires mutual TLS when no client certificate is configured`() {
        MtlsTestServer().use { server ->
            val plugin = plugin(AuthenticationType.NONE) { serverCertificate = TlsFixtures.CA_CERTIFICATE }

            assertThrows<ResourceAccessException> { call(plugin, server.baseUrl) }
            assertNull(server.receivedClientCertificateSubject)
        }
    }

    @Test
    fun `should not trust a server whose CA is not in the JVM trust store when no server certificate is configured`() {
        MtlsTestServer().use { server ->
            val plugin =
                plugin(AuthenticationType.NONE) {
                    clientCertificate = TlsFixtures.CLIENT_CERTIFICATE
                    clientPrivateKey = TlsFixtures.CLIENT_PRIVATE_KEY
                }

            val exception = assertThrows<ResourceAccessException> { call(plugin, server.baseUrl) }

            val causes = generateSequence(exception as Throwable) { it.cause }.toList()
            assertTrue(
                causes.any { it is SSLHandshakeException && it.message!!.contains("PKIX path building failed") },
                causes.joinToString { "${it.javaClass.name}: ${it.message}" },
            )
        }
    }

    @Test
    fun `should trust the server's own certificate as server certificate`() {
        MtlsTestServer().use { server ->
            val plugin =
                plugin(AuthenticationType.NONE) {
                    clientCertificate = TlsFixtures.CLIENT_CERTIFICATE
                    clientPrivateKey = TlsFixtures.CLIENT_PRIVATE_KEY
                    serverCertificate = TlsFixtures.SERVER_CERTIFICATE
                }

            call(plugin, server.baseUrl)

            assertEquals(TlsFixtures.CLIENT_SUBJECT, server.receivedClientCertificateSubject)
        }
    }

    @Test
    fun `should refuse a client certificate without private key`() {
        val plugin = plugin(AuthenticationType.NONE) { clientCertificate = TlsFixtures.CLIENT_CERTIFICATE }

        assertTlsError(plugin, "Property 'clientPrivateKey' is required when 'clientCertificate' is set")
    }

    @Test
    fun `should refuse a private key that does not belong to the client certificate`() {
        val plugin =
            plugin(AuthenticationType.NONE) {
                clientCertificate = TlsFixtures.CLIENT_CERTIFICATE
                clientPrivateKey = TlsFixtures.OTHER_PRIVATE_KEY
            }

        assertTlsError(plugin, "Property 'clientPrivateKey' does not belong to the certificate in 'clientCertificate'")
    }

    @Test
    fun `should accept an RSASSA-PSS client certificate and key`() {
        val plugin =
            plugin(AuthenticationType.NONE) {
                clientCertificate = TlsFixtures.PSS_CLIENT_CERTIFICATE
                clientPrivateKey = TlsFixtures.PSS_CLIENT_PRIVATE_KEY
            }

        plugin.validateConfiguration()
    }

    @Test
    fun `should refuse an RSASSA-PSS private key that does not belong to the client certificate`() {
        val plugin =
            plugin(AuthenticationType.NONE) {
                clientCertificate = TlsFixtures.PSS_CLIENT_CERTIFICATE
                clientPrivateKey = TlsFixtures.PSS_OTHER_PRIVATE_KEY
            }

        assertTlsError(plugin, "Property 'clientPrivateKey' does not belong to the certificate in 'clientCertificate'")
    }

    @Test
    fun `should refuse text that is not PEM, naming the field without echoing it`() {
        val notPem = "not-a-certificate-secret-content"
        assertTlsError(
            plugin(AuthenticationType.NONE) {
                clientCertificate = notPem
                clientPrivateKey = TlsFixtures.CLIENT_PRIVATE_KEY
            },
            "Property 'clientCertificate' does not contain a valid PEM certificate",
        )
        assertTlsError(
            plugin(AuthenticationType.NONE) {
                clientCertificate = TlsFixtures.CLIENT_CERTIFICATE
                clientPrivateKey = notPem
            },
            "Property 'clientPrivateKey' does not contain a valid unencrypted PEM private key",
        )
        assertTlsError(
            plugin(AuthenticationType.NONE) { serverCertificate = notPem },
            "Property 'serverCertificate' does not contain a valid PEM certificate",
        )
        assertTlsError(
            plugin(AuthenticationType.NONE) {
                clientCertificate = TlsFixtures.CLIENT_PRIVATE_KEY
                clientPrivateKey = TlsFixtures.CLIENT_PRIVATE_KEY
            },
            "Property 'clientCertificate' does not contain a valid PEM certificate",
        )
    }

    @Test
    fun `should install Valtimo's connect timeouts and buffering on the mTLS request factory`() {
        val properties =
            ValtimoHttpRestClientConfigurationProperties(
                connectTimeout = 7,
                connectionRequestTimeout = 8,
            )
        val cache = TlsRequestFactoryCache(properties)
        val plugin = plugin(AuthenticationType.NONE, cache) { serverCertificate = TlsFixtures.CA_CERTIFICATE }
        val builder = mock<RestClient.Builder>(defaultAnswer = Answers.RETURNS_SELF)

        plugin.applyAuth(builder)

        val requestFactory =
            argumentCaptor<ClientHttpRequestFactory>()
                .apply { verify(builder).requestFactory(capture()) }
                .firstValue
        assertTrue(requestFactory is BufferingClientHttpRequestFactory)
        val wrapped =
            ReflectionTestUtils.getField(
                requestFactory,
                "requestFactory",
            ) as HttpComponentsClientHttpRequestFactory
        assertEquals(Duration.ofSeconds(7).toMillis(), ReflectionTestUtils.getField(wrapped, "connectTimeout"))
        assertEquals(
            Duration.ofSeconds(8).toMillis(),
            ReflectionTestUtils.getField(wrapped, "connectionRequestTimeout"),
        )
    }

    @Test
    fun `should reuse one request factory per set of certificates`() {
        val cache = TlsRequestFactoryCache(restClientProperties)

        fun requestFactoryOf(plugin: HttpClientAuthenticationPluginBase) =
            argumentCaptor<ClientHttpRequestFactory>()
                .apply {
                    val builder = mock<RestClient.Builder>(defaultAnswer = Answers.RETURNS_SELF)
                    plugin.applyAuth(builder)
                    verify(builder).requestFactory(capture())
                }.firstValue

        val first =
            requestFactoryOf(
                plugin(AuthenticationType.NONE, cache) {
                    serverCertificate =
                        TlsFixtures.CA_CERTIFICATE
                },
            )
        val second =
            requestFactoryOf(
                plugin(AuthenticationType.BEARER, cache) {
                    authSecret = "other"
                    serverCertificate = TlsFixtures.CA_CERTIFICATE
                },
            )
        val third =
            requestFactoryOf(
                plugin(AuthenticationType.NONE, cache) {
                    serverCertificate =
                        TlsFixtures.SERVER_CERTIFICATE
                },
            )

        assertSame(first, second)
        assertFalse(first === third)
    }

    @Test
    fun `should set the header on every request, also behind Valtimo's docker filter`() {
        val builder = RestClient.builder()
        HostDockerInternalRestClientCustomizer(listOf(8080), false, 8080).customize(builder)
        val plugin = plugin(AuthenticationType.BEARER) { authSecret = "secret-bearer-token" }

        val headers = headersOfRequests(plugin, requests = 2, builder = builder)

        assertEquals(
            listOf("Bearer secret-bearer-token", "Bearer secret-bearer-token"),
            headers.map { it.getFirst(HttpHeaders.AUTHORIZATION) },
        )
    }

    @Test
    fun `should make the ZGW JWT per request, for the user of that request`() {
        val plugin =
            plugin(AuthenticationType.ZGW_JWT) {
                clientId = "valtimo_client"
                authSecret = "a-client-secret-of-at-least-32-characters"
            }
        val users = mutableListOf("jan.jansen", "piet.pietersen")

        try {
            val headers =
                headersOfRequests(plugin, requests = 2) {
                    SecurityContextHolder.getContext().authentication =
                        UsernamePasswordAuthenticationToken(
                            users.removeFirst(),
                            null,
                            listOf(SimpleGrantedAuthority("ROLE_USER")),
                        )
                }

            assertEquals(listOf("jan.jansen", "piet.pietersen"), headers.map { claimsOf(it)["user_id"].asText() })
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    @Test
    fun `should send the exchanged token, exchanging once for separate plugin instances`() {
        val tokenCache = TokenExchangeTokenCache(tokenExchangeClient)

        repeat(2) {
            assertEquals(
                "Bearer exchanged-jwt",
                headersOf(tokenExchangePlugin(tokenCache)).getFirst(HttpHeaders.AUTHORIZATION),
            )
        }

        verify(tokenExchangeClient, times(1)).exchangeToken(any(), anyOrNull())
    }

    @Test
    fun `should exchange a new token when the client secret changes`() {
        val tokenCache = TokenExchangeTokenCache(tokenExchangeClient)

        headersOf(tokenExchangePlugin(tokenCache))
        headersOf(tokenExchangePlugin(tokenCache) { clientSecret = "rotated-client-secret" })

        val configs = argumentCaptor<TokenExchangeConfig>()
        verify(tokenExchangeClient, times(2)).exchangeToken(configs.capture(), anyOrNull())
        assertEquals(listOf("client-secret", "rotated-client-secret"), configs.allValues.map { it.clientSecret })
    }

    @Test
    fun `should exchange once for concurrent first requests, share its failure, and retry afterwards`() {
        val exchanging = CountDownLatch(1)
        val release = CountDownLatch(1)
        whenever(tokenExchangeClient.exchangeToken(any(), anyOrNull())).thenAnswer {
            exchanging.countDown()
            release.await(10, TimeUnit.SECONDS)
            throw IllegalStateException("Keycloak is down")
        }
        val tokenCache = TokenExchangeTokenCache(tokenExchangeClient)
        val executor = Executors.newFixedThreadPool(4)

        try {
            val requests =
                (1..4).map {
                    executor.submit<Throwable?> {
                        runCatching {
                            tokenExchangePlugin(
                                tokenCache,
                            ).applyAuth(
                                RestClient.builder(),
                            ).build()
                                .get()
                                .uri(RESOURCE_URL)
                                .retrieve()
                                .toBodilessEntity()
                        }.exceptionOrNull()
                    }
                }
            exchanging.await(10, TimeUnit.SECONDS)
            // The other requests are waiting for the running exchange by now
            Thread.sleep(500)
            release.countDown()

            val failures = requests.map { it.get(10, TimeUnit.SECONDS) }
            assertTrue(
                failures.all { failure ->
                    generateSequence(failure) { it.cause }.any { it.message == "Keycloak is down" }
                },
                failures.toString(),
            )
            verify(tokenExchangeClient, times(1)).exchangeToken(any(), anyOrNull())
        } finally {
            executor.shutdownNow()
        }

        whenever(tokenExchangeClient.exchangeToken(any(), anyOrNull()))
            .thenReturn(TokenExchangeTokenResponse("exchanged-jwt", 300))
        assertEquals(
            "Bearer exchanged-jwt",
            headersOf(tokenExchangePlugin(tokenCache)).getFirst(HttpHeaders.AUTHORIZATION),
        )
    }

    @Test
    fun `should present the client certificate to the token endpoint, also when only the JVM trusts it`() {
        // The CA of the token endpoint is only in the default trust (injected here), the configured one is another
        val cache = TlsRequestFactoryCache(restClientProperties, trustManagerOf(TlsFixtures.CA_CERTIFICATE))
        val keycloak =
            MtlsTestServer(
                responses =
                    mapOf(
                        "POST /token" to """{"access_token":"exchanged-over-mtls","expires_in":300}""",
                    ),
            )
        keycloak.use {
            val plugin =
                tokenExchangePlugin(TokenExchangeTokenCache(TokenExchangeClient(RestClient.builder())), cache) {
                    tokenEndpoint = URI("${keycloak.baseUrl}/token")
                    clientCertificate = TlsFixtures.CLIENT_CERTIFICATE
                    clientPrivateKey = TlsFixtures.CLIENT_PRIVATE_KEY
                    serverCertificate = TlsFixtures.PSS_CLIENT_CERTIFICATE
                }

            val headers = headersOf(plugin)

            assertEquals("Bearer exchanged-over-mtls", headers.getFirst(HttpHeaders.AUTHORIZATION))
            assertEquals(
                listOf(TlsFixtures.CLIENT_SUBJECT, TlsFixtures.CLIENT_SUBJECT),
                keycloak.receivedRequests.map { it.clientCertificateSubject },
            )
        }
    }

    @Test
    fun `should not fall back to the JVM trust for the API calls`() {
        val cache = TlsRequestFactoryCache(restClientProperties, trustManagerOf(TlsFixtures.CA_CERTIFICATE))
        MtlsTestServer().use { server ->
            val plugin =
                plugin(AuthenticationType.NONE, cache) {
                    clientCertificate = TlsFixtures.CLIENT_CERTIFICATE
                    clientPrivateKey = TlsFixtures.CLIENT_PRIVATE_KEY
                    serverCertificate = TlsFixtures.PSS_CLIENT_CERTIFICATE
                }

            assertThrows<ResourceAccessException> { call(plugin, server.baseUrl) }
        }
    }

    @Test
    fun `should present a client certificate from a JKS or PKCS12 keystore`() {
        listOf("JKS", "PKCS12").forEach { type ->
            MtlsTestServer().use { server ->
                val plugin =
                    plugin(AuthenticationType.TOKEN) {
                        authSecret = "secret-token"
                        keystorePath = TlsFixtures.clientKeystore(type, temporaryDirectory).toString()
                        keystoreSecret = TlsFixtures.KEYSTORE_SECRET
                        truststorePath = TlsFixtures.truststore(type, temporaryDirectory).toString()
                        truststoreSecret = TlsFixtures.KEYSTORE_SECRET
                    }

                plugin.validateConfiguration()
                call(plugin, server.baseUrl)

                assertEquals(TlsFixtures.CLIENT_SUBJECT, server.receivedClientCertificateSubject, type)
                assertEquals("Token secret-token", server.receivedAuthorization, type)
            }
        }
    }

    @Test
    fun `should refuse certificates both as PEM and as keystore files`() {
        val pemClientCertificate: HttpClientAuthenticationPluginBase.() -> Unit = {
            clientCertificate = TlsFixtures.CLIENT_CERTIFICATE
            clientPrivateKey = TlsFixtures.CLIENT_PRIVATE_KEY
        }
        val pemServerCertificate: HttpClientAuthenticationPluginBase.() -> Unit = {
            serverCertificate = TlsFixtures.CA_CERTIFICATE
        }
        val keystore: HttpClientAuthenticationPluginBase.() -> Unit = {
            keystorePath = "/certs/client.jks"
            keystoreSecret = "secret"
        }
        val truststore: HttpClientAuthenticationPluginBase.() -> Unit = {
            truststorePath = "/certs/truststore.jks"
            truststoreSecret = "secret"
        }

        listOf(
            listOf(pemClientCertificate, keystore),
            listOf(pemServerCertificate, truststore),
            listOf(keystore, pemServerCertificate),
            listOf(pemClientCertificate, truststore),
        ).forEach { sources ->
            val plugin = plugin(AuthenticationType.NONE) { sources.forEach { it() } }

            val exception = assertThrows<IllegalArgumentException> { plugin.validateConfiguration() }

            assertEquals(
                "Configure the certificates either as PEM (clientCertificate, serverCertificate) or as keystore " +
                    "files (keystorePath, truststorePath), not both",
                exception.message,
            )
        }
    }

    @Test
    fun `should require the secret of a keystore path`() {
        val plugin =
            plugin(AuthenticationType.NONE) {
                keystorePath = "/certs/client.jks"
                truststorePath = "/certs/truststore.jks"
            }

        val exception = assertThrows<IllegalArgumentException> { plugin.validateConfiguration() }

        assertEquals(
            "keystoreSecret is required when keystorePath is configured; " +
                "truststoreSecret is required when truststorePath is configured",
            exception.message,
        )
    }

    @Test
    fun `should save a keystore path this node can't read, and name it on use`() {
        val plugin =
            plugin(AuthenticationType.NONE) {
                keystorePath = temporaryDirectory.resolve("missing.jks").toString()
                keystoreSecret = TlsFixtures.KEYSTORE_SECRET
            }

        plugin.validateConfiguration()
        val exception = assertThrows<IllegalArgumentException> { plugin.applyAuth(RestClient.builder()) }

        assertEquals(
            "Property 'keystorePath' does not point to a readable keystore (NoSuchFileException)",
            exception.message,
        )
    }

    @Test
    fun `should name the keystore secret when it doesn't open the keystore`() {
        listOf("JKS", "PKCS12").forEach { type ->
            val plugin =
                plugin(AuthenticationType.NONE) {
                    keystorePath = TlsFixtures.clientKeystore(type, temporaryDirectory).toString()
                    keystoreSecret = "wrong-secret"
                }

            val exception = assertThrows<IllegalArgumentException>(type) { plugin.applyAuth(RestClient.builder()) }

            assertEquals(
                "Property 'keystoreSecret' does not open the keystore in 'keystorePath'",
                exception.message,
                type,
            )
        }
    }

    @Test
    fun `should ignore stored keystore secrets whose path is empty`() {
        MtlsTestServer().use { server ->
            val plugin =
                plugin(AuthenticationType.NONE) {
                    keystoreSecret = "left-over-secret"
                    truststoreSecret = "left-over-secret"
                    clientCertificate = TlsFixtures.CLIENT_CERTIFICATE
                    clientPrivateKey = TlsFixtures.CLIENT_PRIVATE_KEY
                    serverCertificate = TlsFixtures.CA_CERTIFICATE
                }

            plugin.validateConfiguration()
            call(plugin, server.baseUrl)

            assertEquals(TlsFixtures.CLIENT_SUBJECT, server.receivedClientCertificateSubject)
        }
    }

    @Test
    fun `should make a new request factory when the keystore file is replaced`() {
        val cache = TlsRequestFactoryCache(restClientProperties)
        val keystore = TlsFixtures.clientKeystore("PKCS12", temporaryDirectory)
        val plugin = {
            plugin(AuthenticationType.NONE, cache) {
                keystorePath = keystore.toString()
                keystoreSecret = TlsFixtures.KEYSTORE_SECRET
            }
        }

        val first = requestFactoryOf(plugin())
        assertSame(first, requestFactoryOf(plugin()))
        Files.setLastModifiedTime(
            keystore,
            FileTime.fromMillis(Files.getLastModifiedTime(keystore).toMillis() + 60_000),
        )

        assertFalse(first === requestFactoryOf(plugin()))
    }

    @Test
    fun `should reuse the mutual TLS connection for the next request`() {
        MtlsTestServer().use { server ->
            val plugin =
                plugin(AuthenticationType.NONE) {
                    clientCertificate = TlsFixtures.CLIENT_CERTIFICATE
                    clientPrivateKey = TlsFixtures.CLIENT_PRIVATE_KEY
                    serverCertificate = TlsFixtures.CA_CERTIFICATE
                }

            call(plugin, server.baseUrl)
            call(plugin, server.baseUrl)

            assertEquals(
                1,
                server.receivedRequests
                    .map { it.clientPort }
                    .distinct()
                    .size,
            )
        }
    }

    @Test
    fun `should not limit concurrent mutual TLS calls of one configuration to a few connections`() {
        val cache = TlsRequestFactoryCache(ValtimoHttpRestClientConfigurationProperties(connectionRequestTimeout = 1))
        val executor = Executors.newFixedThreadPool(8)
        MtlsTestServer(responseDelayMillis = 2000).use { server ->
            try {
                val calls =
                    (1..8).map {
                        executor.submit<String?> {
                            // A new plugin instance per call, as Valtimo creates them
                            call(
                                plugin(AuthenticationType.NONE, cache) {
                                    clientCertificate = TlsFixtures.CLIENT_CERTIFICATE
                                    clientPrivateKey = TlsFixtures.CLIENT_PRIVATE_KEY
                                    serverCertificate = TlsFixtures.CA_CERTIFICATE
                                },
                                server.baseUrl,
                            )
                        }
                    }

                assertEquals(List(8) { "{}" }, calls.map { it.get(30, TimeUnit.SECONDS) })
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun `should refuse a private key in a certificate property, which isn't stored encrypted`() {
        assertTlsError(
            plugin(AuthenticationType.NONE) {
                clientCertificate = TlsFixtures.CLIENT_CERTIFICATE + "\n" + TlsFixtures.CLIENT_PRIVATE_KEY
                clientPrivateKey = TlsFixtures.CLIENT_PRIVATE_KEY
            },
            "Property 'clientCertificate' must not contain a private key",
        )
        assertTlsError(
            plugin(AuthenticationType.NONE) {
                serverCertificate = TlsFixtures.CA_CERTIFICATE + "\n" + TlsFixtures.SERVER_PRIVATE_KEY
            },
            "Property 'serverCertificate' must not contain a private key",
        )
    }

    @Test
    fun `should keep the request factory when only a secret without its property changes`() {
        val cache = TlsRequestFactoryCache(restClientProperties)
        val plugin = { leftOverSecret: String ->
            plugin(AuthenticationType.NONE, cache) {
                serverCertificate = TlsFixtures.CA_CERTIFICATE
                clientPrivateKey = leftOverSecret
                keystoreSecret = leftOverSecret
                truststoreSecret = leftOverSecret
            }
        }

        assertSame(requestFactoryOf(plugin("left-over")), requestFactoryOf(plugin("changed")))
    }

    @Test
    fun `should keep the one-argument exchangeToken for callers compiled against 2_1`() {
        TokenExchangeClient::class.java.getMethod("exchangeToken", TokenExchangeConfig::class.java)
    }

    private fun tokenExchangePlugin(
        tokenCache: TokenExchangeTokenCache,
        cache: TlsRequestFactoryCache = TlsRequestFactoryCache(restClientProperties),
        configure: HttpClientAuthenticationPluginBase.() -> Unit = {},
    ) = plugin(AuthenticationType.TOKEN_EXCHANGE, cache, tokenCache) {
        tokenEndpoint = URI("https://keycloak.example.com/token")
        clientId = "client"
        clientSecret = "client-secret"
        audience = "haal-centraal"
        configure()
    }

    private fun requestFactoryOf(plugin: HttpClientAuthenticationPluginBase) =
        argumentCaptor<ClientHttpRequestFactory>()
            .apply {
                val builder = mock<RestClient.Builder>(defaultAnswer = Answers.RETURNS_SELF)
                plugin.applyAuth(builder)
                verify(builder).requestFactory(capture())
            }.firstValue

    private fun trustManagerOf(certificate: String): X509ExtendedTrustManager =
        TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply {
                init(
                    PemSslStoreBundle(null, PemSslStore.of(PemContent.of(certificate).certificates, null)).trustStore,
                )
            }.trustManagers
            .filterIsInstance<X509ExtendedTrustManager>()
            .first()

    private fun claimsOf(headers: HttpHeaders) =
        ObjectMapper().readTree(
            Base64.getUrlDecoder().decode(
                headers.getFirst(HttpHeaders.AUTHORIZATION)!!.removePrefix("Bearer ").split(".")[1],
            ),
        )

    private fun assertTlsError(
        plugin: HttpClientAuthenticationPluginBase,
        message: String,
    ) {
        val onSave = assertThrows<IllegalArgumentException> { plugin.validateConfiguration() }
        val onUse = assertThrows<IllegalArgumentException> { plugin.applyAuth(RestClient.builder()) }

        assertEquals(message, onSave.message)
        assertEquals(message, onUse.message)
    }

    private fun call(
        plugin: HttpClientAuthenticationPluginBase,
        url: String,
    ): String? =
        sharedRestClientBuilder
            .clone()
            .apply { plugin.applyAuth(it) }
            .build()
            .get()
            .uri(url)
            .retrieve()
            .body<String>()

    // The headers of the requests a client built with applyAuth sends, one per request
    private fun headersOfRequests(
        plugin: HttpClientAuthenticationPluginBase,
        requests: Int = 1,
        builder: RestClient.Builder = RestClient.builder(),
        beforeEachRequest: () -> Unit = {},
    ): List<HttpHeaders> {
        val sent = mutableListOf<HttpHeaders>()
        plugin.applyAuth(builder)
        val server = MockRestServiceServer.bindTo(builder).build()
        server
            .expect(ExpectedCount.times(requests), requestTo(RESOURCE_URL))
            .andExpect { request -> sent.add(HttpHeaders().apply { putAll(request.headers) }) }
            .andRespond(withSuccess())
        val restClient = builder.build()
        repeat(requests) {
            beforeEachRequest()
            restClient
                .get()
                .uri(RESOURCE_URL)
                .retrieve()
                .toBodilessEntity()
        }
        server.verify()
        return sent
    }

    private fun headersOf(plugin: HttpClientAuthenticationPluginBase): HttpHeaders = headersOfRequests(plugin).single()

    private fun plugin(
        type: AuthenticationType,
        cache: TlsRequestFactoryCache = TlsRequestFactoryCache(restClientProperties),
        tokenCache: TokenExchangeTokenCache = TokenExchangeTokenCache(tokenExchangeClient),
        configure: HttpClientAuthenticationPluginBase.() -> Unit = {},
    ) = HttpClientAuthenticationPlugin(PluginConfigurationId.newId(), cache, tokenCache).also {
        // Not apply: the plugin is an ExchangeFilterFunction, whose own apply(ExchangeFunction) would win
        it.authenticationType = type
        it.configure()
    }

    companion object {
        private const val RESOURCE_URL = "https://api.example.com/resource"
    }
}

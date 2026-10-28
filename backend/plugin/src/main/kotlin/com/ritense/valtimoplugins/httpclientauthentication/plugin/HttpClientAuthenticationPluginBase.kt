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

import com.ritense.plugin.annotation.PluginEvent
import com.ritense.plugin.annotation.PluginProperty
import com.ritense.plugin.domain.EventType
import com.ritense.plugin.domain.PluginConfigurationId
import com.ritense.valtimoplugins.httpclientauthentication.HttpClientAuthenticator
import com.ritense.valtimoplugins.httpclientauthentication.model.AuthenticationType
import com.ritense.valtimoplugins.httpclientauthentication.saml.SamlTokenClient
import com.ritense.valtimoplugins.httpclientauthentication.tls.TlsManagers
import com.ritense.valtimoplugins.httpclientauthentication.tls.TlsMaterial
import com.ritense.valtimoplugins.httpclientauthentication.tls.TlsRequestFactoryCache
import com.ritense.valtimoplugins.httpclientauthentication.tokenexchange.TokenExchangeConfig
import com.ritense.valtimoplugins.httpclientauthentication.tokenexchange.TokenExchangeTokenCache
import com.ritense.valtimoplugins.httpclientauthentication.zgw.ZgwJwt
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.net.URI
import java.time.Duration
import javax.net.ssl.SSLContext

/**
 * All logic of the plugin. The `@Plugin` class is generated at startup as a subclass that adds the category interfaces
 * of the auth plugins on the classpath, see [GeneratedPluginClass].
 *
 * Nothing here may use a type of an optional module (CXF, reactor-netty, the covered plugins) in a signature: Valtimo
 * and the class generation reflect on every method of the class, which fails when such a type is missing. The support
 * interfaces in [com.ritense.valtimoplugins.httpclientauthentication.support] use what this class exposes.
 */
abstract class HttpClientAuthenticationPluginBase(
    val configurationId: PluginConfigurationId,
    private val tlsRequestFactoryCache: TlsRequestFactoryCache,
    private val tokenExchangeTokenCache: TokenExchangeTokenCache,
    private val samlTokenClient: SamlTokenClient,
) : HttpClientAuthenticator,
    ExchangeFilterFunction {
    @PluginProperty(key = "authenticationType", secret = false, required = true)
    lateinit var authenticationType: AuthenticationType

    // BEARER, HEADER, TOKEN, ZGW_JWT, BASIC, BEARER_AND_API_KEY; the header name also for SAML_TOKEN
    @PluginProperty(key = "authHeaderName", secret = false, required = false)
    var authHeaderName: String? = null

    @PluginProperty(key = "authSecret", secret = true, required = false)
    var authSecret: String? = null

    // BASIC
    @PluginProperty(key = "username", secret = false, required = false)
    var username: String? = null

    // ZGW_JWT, TOKEN_EXCHANGE
    @PluginProperty(key = "clientId", secret = false, required = false)
    var clientId: String? = null

    // TOKEN_EXCHANGE; the token endpoint also for SAML_TOKEN
    @PluginProperty(key = "clientSecret", secret = true, required = false)
    var clientSecret: String? = null

    @PluginProperty(key = "tokenEndpoint", secret = false, required = false)
    var tokenEndpoint: URI? = null

    @PluginProperty(key = "audience", secret = false, required = false)
    var audience: String? = null

    @PluginProperty(key = "scope", secret = false, required = false)
    var scope: String? = null

    // Milliseconds, for the SAML token service and the reactor-netty client. Boxed: Valtimo 12 can't load a primitive
    @PluginProperty(key = "connectionTimeout", secret = false, required = false)
    var connectionTimeout: Int? = null

    @PluginProperty(key = "responseTimeout", secret = false, required = false)
    var responseTimeout: Int? = null

    // Certificates, for every type: PEM or keystore files
    @PluginProperty(key = "clientCertificate", secret = false, required = false)
    var clientCertificate: String? = null

    @PluginProperty(key = "clientPrivateKey", secret = true, required = false)
    var clientPrivateKey: String? = null

    @PluginProperty(key = "serverCertificate", secret = false, required = false)
    var serverCertificate: String? = null

    @PluginProperty(key = "keystorePath", secret = false, required = false)
    var keystorePath: String? = null

    @PluginProperty(key = "keystoreSecret", secret = true, required = false)
    var keystoreSecret: String? = null

    @PluginProperty(key = "truststorePath", secret = false, required = false)
    var truststorePath: String? = null

    @PluginProperty(key = "truststoreSecret", secret = true, required = false)
    var truststoreSecret: String? = null

    /**
     * Checks the configuration when it's saved or autodeployed. Reads no files and calls no servers, so a configuration
     * is judged the same on every node; a keystore that can't be read fails on first use.
     */
    @PluginEvent(invokedOn = [EventType.CREATE, EventType.UPDATE])
    fun validateConfiguration() {
        val problems = listOfNotNull(authenticationProblem(), timeoutProblem()) + tlsMaterial().problems()
        require(problems.isEmpty()) { problems.joinToString("; ") }
    }

    /**
     * The credentials are set per request by a request initializer: a long-lived client never sends an expired token,
     * and, unlike an interceptor, it isn't lost behind the interceptors Valtimo adds (such as its docker filter).
     */
    override fun applyAuth(builder: RestClient.Builder): RestClient.Builder {
        requireAuthentication()
        apiTlsMaterial()?.let { builder.requestFactory(tlsRequestFactoryCache.requestFactory(it)) }
        if (authenticationType != AuthenticationType.NONE) {
            builder.requestInitializer { request -> setAuthenticationHeaders(request.headers) }
        }
        return builder
    }

    /**
     * Sets the same headers on WebClient requests. A WebClient keeps its own connector, so certificates don't apply.
     */
    override fun filter(
        request: ClientRequest,
        next: ExchangeFunction,
    ): Mono<ClientResponse> {
        requireAuthentication()
        return authenticationHeadersMono().flatMap { headers ->
            next.exchange(ClientRequest.from(request).headers { it.putAll(headers) }.build())
        }
    }

    /** [authenticationHeaders] for reactive clients: a token service blocks, so it isn't called on the event loop. */
    internal fun authenticationHeadersMono(): Mono<HttpHeaders> =
        if (authenticationType in TOKEN_SERVICE_TYPES) {
            Mono.fromCallable { authenticationHeaders() }.subscribeOn(Schedulers.boundedElastic())
        } else {
            Mono.fromCallable { authenticationHeaders() }
        }

    /** The headers this configuration sends with a request; may call a token service. */
    override fun authenticationHeaders(): HttpHeaders {
        requireAuthentication()
        return HttpHeaders().apply {
            when (authenticationType) {
                AuthenticationType.NONE -> Unit
                AuthenticationType.HEADER -> set(required(authHeaderName), headerSecret())
                AuthenticationType.TOKEN -> set(HttpHeaders.AUTHORIZATION, "Token ${headerSecret()}")
                AuthenticationType.BASIC -> setBasicAuth(required(username), required(authSecret), Charsets.UTF_8)
                AuthenticationType.BEARER_AND_API_KEY -> {
                    setBearerAuth(token())
                    set(API_KEY_HEADER, headerSecret())
                }
                AuthenticationType.SAML_TOKEN -> {
                    val headerName = authHeaderName?.takeIf { it.isNotBlank() }
                    if (headerName != null) set(headerName, token()) else setBearerAuth(token())
                }
                AuthenticationType.BEARER, AuthenticationType.ZGW_JWT, AuthenticationType.TOKEN_EXCHANGE ->
                    setBearerAuth(token())
            }
        }
    }

    /** The token this configuration sends as bearer token, for callers that send it themselves. */
    internal fun bearerToken(): String {
        requireAuthentication()
        return token()
    }

    private fun token(): String =
        when (authenticationType) {
            AuthenticationType.BEARER, AuthenticationType.BEARER_AND_API_KEY -> headerSecret()
            AuthenticationType.ZGW_JWT -> ZgwJwt.generate(required(clientId), required(authSecret))
            AuthenticationType.TOKEN_EXCHANGE -> exchangedToken()
            AuthenticationType.SAML_TOKEN -> samlToken()
            else -> throw IllegalStateException("Authentication type $authenticationType has no bearer token")
        }

    /**
     * The certificates for the API calls, or null when none apply. Those of SAML_TOKEN are for the token service only:
     * its truststore usually holds only the token service's CA.
     */
    internal fun apiTlsMaterial(): TlsMaterial? =
        tlsMaterial().takeIf { it.isConfigured && authenticationType != AuthenticationType.SAML_TOKEN }

    internal fun apiTlsManagers(): TlsManagers? = apiTlsMaterial()?.let(tlsRequestFactoryCache::managers)

    override fun sslContext(): SSLContext? = apiTlsMaterial()?.let(tlsRequestFactoryCache::sslContext)

    internal fun requiredApiSslContext(): SSLContext =
        checkNotNull(sslContext()) {
            if (authenticationType == AuthenticationType.SAML_TOKEN) {
                "Authentication type $authenticationType uses its certificates for the token service only"
            } else {
                "No certificates are configured"
            }
        }

    internal fun connectionTimeoutDuration(): Duration =
        Duration.ofMillis((connectionTimeout ?: DEFAULT_TIMEOUT).toLong())

    internal fun responseTimeoutDuration(): Duration = Duration.ofMillis((responseTimeout ?: DEFAULT_TIMEOUT).toLong())

    internal fun requireAuthentication() {
        authenticationProblem()?.let { throw IllegalArgumentException(it) }
    }

    private fun setAuthenticationHeaders(headers: HttpHeaders) = headers.putAll(authenticationHeaders())

    private fun exchangedToken(): String {
        val tlsMaterial = tlsMaterial()
        val config =
            TokenExchangeConfig(
                tokenEndpoint = requireNotNull(tokenEndpoint),
                clientId = required(clientId),
                clientSecret = required(clientSecret),
                audience = required(audience),
                scope = scope?.takeIf { it.isNotBlank() },
            )
        // The token endpoint gets the client certificate too
        return tokenExchangeTokenCache.accessToken(config, tlsMaterial.digest) {
            tlsMaterial.takeIf { it.isConfigured }?.let(tlsRequestFactoryCache::tokenEndpointRequestFactory)
        }
    }

    private fun samlToken(): String {
        val requestFactory =
            tlsRequestFactoryCache.samlTokenServiceRequestFactory(
                tlsMaterial(),
                connectionTimeoutDuration(),
                responseTimeoutDuration(),
            )
        return samlTokenClient.token(requireNotNull(tokenEndpoint), requestFactory)
    }

    private fun authenticationProblem(): String? {
        val missing =
            requiredProperties()
                .filter { (_, value) -> value.isNullOrBlank() }
                .map { (property, _) -> property }
        if (missing.isNotEmpty()) {
            return "Authentication type $authenticationType requires the properties: ${missing.joinToString()}"
        }
        val headerName = authHeaderName?.takeIf { it.isNotBlank() }
        if (authenticationType in HEADER_NAME_TYPES && headerName != null && !HEADER_NAME.matches(headerName)) {
            return "Property 'authHeaderName' is not a valid header name"
        }
        headerValueProperties()
            .firstOrNull { (_, value) -> value != null && !isHeaderValue(value.trim()) }
            ?.let { (property, _) ->
                return "Property '$property' contains a character that can't be sent in a header " +
                    "(a control character or one above U+00FF)"
            }
        return when (authenticationType) {
            AuthenticationType.ZGW_JWT ->
                if (authSecret!!.length < ZgwJwt.MINIMUM_SECRET_LENGTH) {
                    "Property 'authSecret' must be at least ${ZgwJwt.MINIMUM_SECRET_LENGTH} characters " +
                        "for authentication type $authenticationType"
                } else {
                    null
                }
            AuthenticationType.BASIC ->
                if (username!!.contains(':')) "Property 'username' must not contain ':'" else null
            AuthenticationType.SAML_TOKEN ->
                if (!tokenEndpoint!!.scheme.equals("https", ignoreCase = true)) {
                    "Property 'tokenEndpoint' must be an https URL for authentication type $authenticationType"
                } else {
                    null
                }
            else -> null
        }
    }

    private fun timeoutProblem(): String? =
        listOf("connectionTimeout" to connectionTimeout, "responseTimeout" to responseTimeout)
            .firstOrNull { (_, value) -> value != null && value <= 0 }
            ?.let { (property, _) -> "Property '$property' must be a positive number of milliseconds" }

    private fun requiredProperties(): List<Pair<String, String?>> =
        when (authenticationType) {
            AuthenticationType.NONE -> emptyList()
            AuthenticationType.BEARER, AuthenticationType.TOKEN, AuthenticationType.BEARER_AND_API_KEY ->
                listOf("authSecret" to authSecret)
            AuthenticationType.HEADER -> listOf("authHeaderName" to authHeaderName, "authSecret" to authSecret)
            AuthenticationType.ZGW_JWT -> listOf("clientId" to clientId, "authSecret" to authSecret)
            AuthenticationType.TOKEN_EXCHANGE ->
                listOf(
                    "tokenEndpoint" to tokenEndpoint?.toString(),
                    "clientId" to clientId,
                    "clientSecret" to clientSecret,
                    "audience" to audience,
                )
            AuthenticationType.BASIC -> listOf("username" to username, "authSecret" to authSecret)
            AuthenticationType.SAML_TOKEN -> listOf("tokenEndpoint" to tokenEndpoint?.toString())
        }

    // The values sent as a header, trimmed (Basic credentials are encoded). The JDK's HTTP clients (used by CXF)
    // refuse other characters with the value in the message
    private fun headerValueProperties(): List<Pair<String, String?>> =
        when (authenticationType) {
            AuthenticationType.BEARER,
            AuthenticationType.HEADER,
            AuthenticationType.TOKEN,
            AuthenticationType.BEARER_AND_API_KEY,
            -> listOf("authSecret" to authSecret)
            else -> emptyList()
        }

    // Visible ASCII, space, tab and Latin-1 without its control characters
    private fun isHeaderValue(value: String) = value.all { it == '\t' || it in ' '..'~' || it in '\u00A0'..'\u00FF' }

    // Checked by authenticationProblem before the header is set
    private fun required(value: String?): String = requireNotNull(value?.takeIf { it.isNotBlank() })

    // Without surrounding whitespace, such as the line break of a secret from a file or environment variable: the HTTP
    // clients and servers dropped it before 3.1.0 too
    private fun headerSecret(): String = required(authSecret).trim()

    private fun tlsMaterial() =
        TlsMaterial(
            clientCertificate = clientCertificate,
            clientPrivateKey = clientPrivateKey,
            serverCertificate = serverCertificate,
            keystorePath = keystorePath,
            keystoreSecret = keystoreSecret,
            truststorePath = truststorePath,
            truststoreSecret = truststoreSecret,
        )

    companion object {
        // An HTTP token (RFC 9110)
        private val HEADER_NAME = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
        private val HEADER_NAME_TYPES = setOf(AuthenticationType.HEADER, AuthenticationType.SAML_TOKEN)
        private val TOKEN_SERVICE_TYPES = setOf(AuthenticationType.TOKEN_EXCHANGE, AuthenticationType.SAML_TOKEN)

        const val API_KEY_HEADER = "X-Api-Key"
        const val DEFAULT_TIMEOUT = 10_000

        const val PLUGIN_KEY = "http-client-authentication"
        const val PLUGIN_TITLE = "Http Client Authentication"
        const val PLUGIN_DESCRIPTION =
            "Provides authentication and mutual TLS for outbound REST and SOAP clients, including the ZGW API, " +
                "Haal Centraal and Suwinet plugins"
    }
}

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
import com.ritense.plugin.domain.EventType
import com.ritense.plugin.domain.PluginConfigurationId
import com.ritense.valtimoplugins.httpclientauthentication.HttpClientAuthenticator
import com.ritense.valtimoplugins.httpclientauthentication.model.AuthenticationType
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
import java.net.URI

/**
 * All logic of the plugin. The `@Plugin` classes are generated variants of this class, one per combination of ZGW
 * modules, that add the ZGW authentication interfaces and declare the properties (Valtimo only reads declared fields).
 * See [com.ritense.valtimoplugins.httpclientauthentication.variant.PluginVariants].
 */
abstract class HttpClientAuthenticationPluginBase(
    val configurationId: PluginConfigurationId,
    private val tlsRequestFactoryCache: TlsRequestFactoryCache,
    private val tokenExchangeTokenCache: TokenExchangeTokenCache,
) : HttpClientAuthenticator,
    ExchangeFilterFunction {
    abstract var authenticationType: AuthenticationType

    // BEARER, HEADER, TOKEN, ZGW_JWT
    abstract var authHeaderName: String?
    abstract var authSecret: String?

    // ZGW_JWT, TOKEN_EXCHANGE
    abstract var clientId: String?

    // TOKEN_EXCHANGE
    abstract var clientSecret: String?
    abstract var tokenEndpoint: URI?
    abstract var audience: String?
    abstract var scope: String?

    // Certificates, for every type: PEM or keystore files
    abstract var clientCertificate: String?
    abstract var clientPrivateKey: String?
    abstract var serverCertificate: String?
    abstract var keystorePath: String?
    abstract var keystoreSecret: String?
    abstract var truststorePath: String?
    abstract var truststoreSecret: String?

    /**
     * Checks the configuration when it's saved or autodeployed. Reads no files and calls no servers, so a configuration
     * is judged the same on every node; a keystore that can't be read fails on first use.
     */
    @PluginEvent(invokedOn = [EventType.CREATE, EventType.UPDATE])
    fun validateConfiguration() {
        val problems = listOfNotNull(authenticationProblem()) + tlsMaterial().problems()
        require(problems.isEmpty()) { problems.joinToString("; ") }
    }

    /**
     * The credentials are set per request by a request initializer: a long-lived client never sends an expired token,
     * and, unlike an interceptor, it isn't lost behind the interceptors Valtimo adds (such as its docker filter).
     */
    override fun applyAuth(builder: RestClient.Builder): RestClient.Builder {
        requireAuthentication()
        val tlsMaterial = tlsMaterial()
        if (tlsMaterial.isConfigured) {
            builder.requestFactory(tlsRequestFactoryCache.requestFactory(tlsMaterial))
        }
        if (authenticationType != AuthenticationType.NONE) {
            builder.requestInitializer { request -> setAuthHeaders(request.headers, tlsMaterial) }
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
        val tlsMaterial = tlsMaterial()
        val filteredRequest =
            ClientRequest
                .from(request)
                .headers { headers -> setAuthHeaders(headers, tlsMaterial) }
                .build()
        return next.exchange(filteredRequest)
    }

    private fun setAuthHeaders(
        headers: HttpHeaders,
        tlsMaterial: TlsMaterial,
    ) {
        when (authenticationType) {
            AuthenticationType.BEARER -> headers.setBearerAuth(required(authSecret))
            AuthenticationType.HEADER -> headers.set(required(authHeaderName), required(authSecret))
            AuthenticationType.TOKEN -> headers.set(HttpHeaders.AUTHORIZATION, "Token ${required(authSecret)}")
            AuthenticationType.ZGW_JWT ->
                headers.setBearerAuth(
                    ZgwJwt.generate(required(clientId), required(authSecret)),
                )
            AuthenticationType.TOKEN_EXCHANGE -> headers.setBearerAuth(exchangedToken(tlsMaterial))
            AuthenticationType.NONE -> Unit
        }
    }

    private fun exchangedToken(tlsMaterial: TlsMaterial): String {
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

    private fun requireAuthentication() {
        authenticationProblem()?.let { throw IllegalArgumentException(it) }
    }

    private fun authenticationProblem(): String? {
        val missing =
            requiredProperties()
                .filter { (_, value) -> value.isNullOrBlank() }
                .map { (property, _) -> property }
        if (missing.isNotEmpty()) {
            return "Authentication type $authenticationType requires the properties: ${missing.joinToString()}"
        }
        if (authenticationType == AuthenticationType.HEADER && !HEADER_NAME.matches(authHeaderName!!)) {
            return "Property 'authHeaderName' is not a valid header name"
        }
        if (authenticationType == AuthenticationType.ZGW_JWT && authSecret!!.length < ZgwJwt.MINIMUM_SECRET_LENGTH) {
            return "Property 'authSecret' must be at least ${ZgwJwt.MINIMUM_SECRET_LENGTH} characters " +
                "for authentication type $authenticationType"
        }
        return null
    }

    private fun requiredProperties(): List<Pair<String, String?>> =
        when (authenticationType) {
            AuthenticationType.NONE -> emptyList()
            AuthenticationType.BEARER, AuthenticationType.TOKEN -> listOf("authSecret" to authSecret)
            AuthenticationType.HEADER -> listOf("authHeaderName" to authHeaderName, "authSecret" to authSecret)
            AuthenticationType.ZGW_JWT -> listOf("clientId" to clientId, "authSecret" to authSecret)
            AuthenticationType.TOKEN_EXCHANGE ->
                listOf(
                    "tokenEndpoint" to tokenEndpoint?.toString(),
                    "clientId" to clientId,
                    "clientSecret" to clientSecret,
                    "audience" to audience,
                )
        }

    // Checked by authenticationProblem before the header is set
    private fun required(value: String?): String = requireNotNull(value?.takeIf { it.isNotBlank() })

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

        const val PLUGIN_KEY = "http-client-authentication"
        const val PLUGIN_TITLE = "Http Client Authentication"
        const val PLUGIN_DESCRIPTION =
            "Provides authentication and mutual TLS for outbound REST clients, including the ZGW API plugins"
    }
}

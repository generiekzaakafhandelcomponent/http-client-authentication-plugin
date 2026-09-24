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

import com.ritense.plugin.annotation.Plugin
import com.ritense.plugin.annotation.PluginEvent
import com.ritense.plugin.annotation.PluginProperty
import com.ritense.plugin.domain.EventType
import com.ritense.valtimoplugins.httpclientauthentication.HttpClientAuthenticator
import com.ritense.valtimoplugins.httpclientauthentication.model.AuthenticationType
import com.ritense.valtimoplugins.httpclientauthentication.tokenexchange.CachedAccessToken
import com.ritense.valtimoplugins.httpclientauthentication.tokenexchange.MtlsContextFactory
import com.ritense.valtimoplugins.httpclientauthentication.tokenexchange.TokenExchangeClient
import com.ritense.valtimoplugins.httpclientauthentication.tokenexchange.TokenExchangeConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.URI
import java.net.http.HttpClient
import javax.net.ssl.SSLContext

/**
 * Authenticates outbound REST clients using one of the [AuthenticationType]s. For [AuthenticationType.TOKEN_EXCHANGE],
 * a client certificate (mTLS) is presented when a keystore is configured.
 */
@Plugin(
    key = "http-client-authentication-plugin",
    title = "Http Client Authentication Plugin",
    description = "Provides authentication headers for outbound REST clients"
)
@Suppress("UNUSED")
class HttpClientAuthenticationPlugin(
    private val tokenExchangeClient: TokenExchangeClient,
) : HttpClientAuthenticator {

    @PluginProperty(key = "authenticationType", secret = false, required = true)
    lateinit var authenticationType: AuthenticationType

    // BEARER / HEADER
    @PluginProperty(key = "authHeaderName", secret = false, required = false)
    var authHeaderName: String? = null

    @PluginProperty(key = "authSecret", secret = true, required = false)
    var authSecret: String? = null

    // TOKEN_EXCHANGE
    @PluginProperty(key = "tokenEndpoint", secret = false, required = false)
    var tokenEndpoint: URI? = null

    @PluginProperty(key = "clientId", secret = false, required = false)
    var clientId: String? = null

    @PluginProperty(key = "clientSecret", secret = true, required = false)
    var clientSecret: String? = null

    @PluginProperty(key = "audience", secret = false, required = false)
    var audience: String? = null

    @PluginProperty(key = "scope", secret = false, required = false)
    var scope: String? = null

    // mTLS, optional for TOKEN_EXCHANGE only
    @PluginProperty(key = "keystorePath", secret = false, required = false)
    var keystorePath: String? = null

    @PluginProperty(key = "keystoreSecret", secret = true, required = false)
    var keystoreSecret: String? = null

    @PluginProperty(key = "truststorePath", secret = false, required = false)
    var truststorePath: String? = null

    @PluginProperty(key = "truststoreSecret", secret = true, required = false)
    var truststoreSecret: String? = null

    @Volatile
    private var cachedAccessToken: CachedAccessToken? = null
    private val sslContext: SSLContext? by lazy {
        val ksPath = keystorePath
        val ksSecret = keystoreSecret
        if (ksPath.isNullOrBlank() || ksSecret.isNullOrBlank()) {
            null
        } else {
            MtlsContextFactory.createFromKeystore(
                keystorePath = ksPath,
                keystoreSecret = ksSecret,
                truststorePath = truststorePath?.takeIf { it.isNotBlank() },
                truststoreSecret = truststoreSecret,
            )
        }
    }

    @PluginEvent(invokedOn = [EventType.CREATE, EventType.UPDATE])
    fun validateProperties() {
        when (authenticationType) {
            AuthenticationType.NONE -> Unit
            AuthenticationType.BEARER -> requireProperties("authSecret" to authSecret)
            AuthenticationType.HEADER -> requireProperties(
                "authHeaderName" to authHeaderName,
                "authSecret" to authSecret,
            )
            AuthenticationType.TOKEN_EXCHANGE -> requireProperties(
                "tokenEndpoint" to tokenEndpoint?.toString(),
                "clientId" to clientId,
                "clientSecret" to clientSecret,
                "audience" to audience,
            )
        }
        if (authenticationType == AuthenticationType.TOKEN_EXCHANGE) {
            if (!keystorePath.isNullOrBlank()) {
                require(!keystoreSecret.isNullOrBlank()) {
                    "keystoreSecret is required when keystorePath is configured"
                }
            }
        } else {
            require(keystorePath.isNullOrBlank() && truststorePath.isNullOrBlank()) {
                "mTLS (keystorePath, truststorePath) is only supported for authentication type TOKEN_EXCHANGE"
            }
        }
    }

    /**
     * Credentials are applied per request through an interceptor, so a long-lived client never
     * sends an expired token exchange JWT.
     */
    override fun applyAuth(builder: RestClient.Builder): RestClient.Builder {
        when (authenticationType) {
            AuthenticationType.NONE -> Unit
            AuthenticationType.BEARER -> builder.requestInterceptor { request, body, execution ->
                request.headers.setBearerAuth(requireNotNull(authSecret))
                execution.execute(request, body)
            }
            AuthenticationType.HEADER -> builder.requestInterceptor { request, body, execution ->
                request.headers.set(requireNotNull(authHeaderName), requireNotNull(authSecret))
                execution.execute(request, body)
            }
            AuthenticationType.TOKEN_EXCHANGE -> builder.requestInterceptor { request, body, execution ->
                request.headers.setBearerAuth(getAccessToken())
                execution.execute(request, body)
            }
        }
        sslContext?.takeIf { authenticationType == AuthenticationType.TOKEN_EXCHANGE }?.let { sslContext ->
            val httpClient = HttpClient.newBuilder().sslContext(sslContext).build()
            builder.requestFactory(JdkClientHttpRequestFactory(httpClient))
        }
        return builder
    }

    private fun getAccessToken(): String {
        cachedAccessToken?.takeUnless { it.isExpired() }?.let { return it.accessToken }

        synchronized(this) {
            cachedAccessToken?.takeUnless { it.isExpired() }?.let { return it.accessToken }

            logger.debug { "Exchanging a new JWT via Keycloak token-exchange for audience '$audience'" }

            val response = tokenExchangeClient.exchangeToken(
                TokenExchangeConfig(
                    tokenEndpoint = requireNotNull(tokenEndpoint),
                    clientId = requireNotNull(clientId),
                    clientSecret = requireNotNull(clientSecret),
                    audience = requireNotNull(audience),
                    scope = scope,
                )
            )

            return CachedAccessToken(response.accessToken!!, response.expiresIn)
                .also { cachedAccessToken = it }
                .accessToken
        }
    }

    private fun requireProperties(vararg properties: Pair<String, String?>) {
        val missing = properties.filter { (_, value) -> value.isNullOrBlank() }.map { it.first }
        require(missing.isEmpty()) {
            "Authentication type $authenticationType requires the properties: ${missing.joinToString()}"
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

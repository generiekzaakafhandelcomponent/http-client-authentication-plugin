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

package com.ritense.valtimoplugins.httpclientauthentication.tls

import com.ritense.valtimo.contract.client.ValtimoHttpRestClientConfigurationProperties
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import java.security.KeyStore
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager

// Plugin instances are rebuilt per use, so the connection pools live here instead
class TlsRequestFactoryCache(
    private val restClientProperties: ValtimoHttpRestClientConfigurationProperties,
    defaultTrustManager: X509ExtendedTrustManager? = null,
) {
    // Only loaded when a token endpoint needs it
    private val defaultTrust by lazy { defaultTrustManager ?: jvmDefaultTrustManager() }

    private val requestFactories = ConcurrentHashMap<String, ClientHttpRequestFactory>()
    private val managers = ConcurrentHashMap<String, TlsManagers>()
    private val sslContexts = ConcurrentHashMap<String, SSLContext>()

    /**
     * For clients that take key and trust managers (CXF, netty). The same instances for the same certificates: CXF
     * shares an HTTP client between conduits with equal TLS parameters, which compares the managers by instance.
     */
    fun managers(tlsMaterial: TlsMaterial): TlsManagers =
        managers.computeIfAbsent(tlsMaterial.digest) { tlsMaterial.managers() }

    /** For callers that apply the SSL context themselves: trusts only the configured server certificates, if any. */
    fun sslContext(tlsMaterial: TlsMaterial): SSLContext =
        sslContexts.computeIfAbsent(tlsMaterial.digest) { tlsMaterial.sslContext() }

    /** For the API calls: trusts only the configured server certificates, if any. */
    fun requestFactory(tlsMaterial: TlsMaterial): ClientHttpRequestFactory =
        requestFactories.computeIfAbsent(tlsMaterial.digest) { createRequestFactory(tlsMaterial.sslContext()) }

    /** For the token endpoint: also trusts the servers the JVM trusts. */
    fun tokenEndpointRequestFactory(tlsMaterial: TlsMaterial): ClientHttpRequestFactory =
        requestFactories.computeIfAbsent("token-endpoint:${tlsMaterial.digest}") {
            createRequestFactory(tlsMaterial.sslContextAlsoTrusting(defaultTrust))
        }

    /**
     * For a SAML token service: the client certificate, and only the configured server certificates if any (like the
     * original haal-centraal-authentication-plugin), with the configuration's own timeouts, also without certificates.
     */
    fun samlTokenServiceRequestFactory(
        tlsMaterial: TlsMaterial,
        connectTimeout: Duration,
        readTimeout: Duration,
    ): ClientHttpRequestFactory =
        requestFactories.computeIfAbsent(
            "saml:${tlsMaterial.digest}:${connectTimeout.toMillis()}:${readTimeout.toMillis()}",
        ) {
            val sslContext = if (tlsMaterial.isConfigured) tlsMaterial.sslContext() else SSLContext.getDefault()
            createRequestFactory(sslContext, connectTimeout, readTimeout)
        }

    private fun createRequestFactory(
        sslContext: SSLContext,
        connectTimeout: Duration = Duration.ofSeconds(restClientProperties.connectTimeout),
        // None for the API calls, as Valtimo 12
        readTimeout: Duration? = null,
    ): ClientHttpRequestFactory {
        val connectionManager =
            PoolingHttpClientConnectionManagerBuilder
                .create()
                .setTlsSocketStrategy(DefaultClientTlsStrategy(sslContext))
                // One pool serves every client of the configuration, so Apache's default of 5 is too few
                .setMaxConnPerRoute(MAX_CONNECTIONS_PER_ROUTE)
                .setMaxConnTotal(MAX_CONNECTIONS)
                .build()
        val httpClient =
            HttpClients
                .custom()
                .useSystemProperties()
                .setConnectionManager(connectionManager)
                // The pool is per certificate set already; its client principal as state would block reuse
                .disableConnectionState()
                .build()
        // Same timeouts and buffering as Valtimo's ApacheRequestFactoryCustomizer, which has no read timeout on Valtimo 12
        val requestFactory =
            HttpComponentsClientHttpRequestFactory(httpClient).apply {
                setConnectTimeout(connectTimeout)
                setConnectionRequestTimeout(Duration.ofSeconds(restClientProperties.connectionRequestTimeout))
                readTimeout?.let(::setReadTimeout)
            }
        return BufferingClientHttpRequestFactory(requestFactory)
    }

    private fun jvmDefaultTrustManager(): X509ExtendedTrustManager =
        TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers
            .filterIsInstance<X509ExtendedTrustManager>()
            .first()

    companion object {
        const val MAX_CONNECTIONS_PER_ROUTE = 50
        const val MAX_CONNECTIONS = 200
    }
}

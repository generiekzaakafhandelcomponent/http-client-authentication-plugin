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
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.X509ExtendedTrustManager

// Plugin instances are rebuilt per use, so the connection pools live here instead
class TlsRequestFactoryCache(
    private val restClientProperties: ValtimoHttpRestClientConfigurationProperties,
    private val defaultTrustManager: X509ExtendedTrustManager = TlsMaterial.jvmDefaultTrustManager(),
) {
    private val requestFactories = ConcurrentHashMap<String, ClientHttpRequestFactory>()

    /** For the API calls: trusts only the configured server certificates, if any. */
    fun requestFactory(tlsMaterial: TlsMaterial): ClientHttpRequestFactory =
        requestFactories.computeIfAbsent(tlsMaterial.digest) { createRequestFactory(tlsMaterial.sslContext()) }

    /** For the token endpoint: also trusts the servers the JVM trusts. */
    fun tokenEndpointRequestFactory(tlsMaterial: TlsMaterial): ClientHttpRequestFactory =
        requestFactories.computeIfAbsent("token-endpoint:${tlsMaterial.digest}") {
            createRequestFactory(tlsMaterial.sslContextAlsoTrusting(defaultTrustManager))
        }

    private fun createRequestFactory(sslContext: SSLContext): ClientHttpRequestFactory {
        val connectionManager =
            PoolingHttpClientConnectionManagerBuilder
                .create()
                .setTlsSocketStrategy(DefaultClientTlsStrategy(sslContext))
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
                setConnectTimeout(Duration.ofSeconds(restClientProperties.connectTimeout))
                setConnectionRequestTimeout(Duration.ofSeconds(restClientProperties.connectionRequestTimeout))
            }
        return BufferingClientHttpRequestFactory(requestFactory)
    }
}

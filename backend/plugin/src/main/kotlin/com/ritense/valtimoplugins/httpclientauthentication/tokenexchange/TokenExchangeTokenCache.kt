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

package com.ritense.valtimoplugins.httpclientauthentication.tokenexchange

import com.ritense.valtimoplugins.httpclientauthentication.sha256Hex
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.client.ClientHttpRequestFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps exchanged tokens until shortly before they expire. Plugin instances are rebuilt per use, so the tokens live
 * here instead. Concurrent requests for the same configuration wait for one exchange and share its result or failure.
 */
class TokenExchangeTokenCache(
    private val tokenExchangeClient: TokenExchangeClient,
) {
    private val tokens = ConcurrentHashMap<String, CachedAccessToken>()
    private val exchanges = ConcurrentHashMap<String, CompletableFuture<CachedAccessToken>>()

    /**
     * @param tlsKey identifies the certificates the token endpoint is called with
     * @param requestFactory the request factory for the token endpoint, or null for the application's
     */
    fun accessToken(
        config: TokenExchangeConfig,
        tlsKey: String,
        requestFactory: () -> ClientHttpRequestFactory?,
    ): String {
        val key = keyOf(config, tlsKey)
        validToken(key)?.let { return it.accessToken }

        val exchange = CompletableFuture<CachedAccessToken>()
        val running = exchanges.putIfAbsent(key, exchange)
        if (running != null) {
            return try {
                running.join().accessToken
            } catch (e: CompletionException) {
                throw e.cause ?: e
            }
        }
        try {
            // Another exchange may have finished between the first check and claiming this one
            val token =
                validToken(key) ?: exchangeToken(config, requestFactory()).also {
                    tokens.values.removeIf(CachedAccessToken::isExpired)
                    tokens[key] = it
                }
            exchange.complete(token)
            return token.accessToken
        } catch (e: Throwable) {
            exchange.completeExceptionally(e)
            throw e
        } finally {
            exchanges.remove(key, exchange)
        }
    }

    private fun validToken(key: String): CachedAccessToken? = tokens[key]?.takeUnless { it.isExpired() }

    private fun exchangeToken(
        config: TokenExchangeConfig,
        requestFactory: ClientHttpRequestFactory?,
    ): CachedAccessToken {
        logger.debug { "Exchanging a new JWT via Keycloak token-exchange for audience '${config.audience}'" }
        val response = tokenExchangeClient.exchangeToken(config, requestFactory)
        return CachedAccessToken(requireNotNull(response.accessToken), response.expiresIn)
    }

    // Includes the secret, so a changed secret gets a new token
    private fun keyOf(
        config: TokenExchangeConfig,
        tlsKey: String,
    ): String =
        sha256Hex(
            config.tokenEndpoint.toString(),
            config.clientId,
            config.clientSecret,
            config.audience,
            config.scope,
            tlsKey,
        )

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

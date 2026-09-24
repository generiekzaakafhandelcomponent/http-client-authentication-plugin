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

import com.ritense.valtimo.contract.annotation.SkipComponentScan
import com.ritense.valtimoplugins.httpclientauthentication.exception.TokenExchangeException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body

/**
 * Generic Keycloak/OIDC "client_credentials" + "token-exchange" client.
 *
 * This class has no knowledge of Haal Centraal, or of any other API that ends up consuming the
 * exchanged token. Any plugin that needs to authenticate via Keycloak token-exchange can reuse it.
 */
@Component
@SkipComponentScan
class TokenExchangeClient(
    private val restClientBuilder: RestClient.Builder,
) {

    fun exchangeToken(config: TokenExchangeConfig): TokenExchangeTokenResponse {
        val subjectToken = requestToken(
            config.tokenEndpoint,
            formOf(
                "grant_type" to GRANT_TYPE_CLIENT_CREDENTIALS,
                "client_id" to config.clientId,
                "client_secret" to config.clientSecret,
                "scope" to config.scope,
            ),
            "client_credentials",
        )

        return requestToken(
            config.tokenEndpoint,
            formOf(
                "grant_type" to GRANT_TYPE_TOKEN_EXCHANGE,
                "client_id" to config.clientId,
                "client_secret" to config.clientSecret,
                "subject_token" to subjectToken.accessToken,
                "subject_token_type" to SUBJECT_TOKEN_TYPE_ACCESS_TOKEN,
                "audience" to config.audience,
            ),
            "token-exchange",
        )
    }

    private fun requestToken(
        tokenEndpoint: java.net.URI,
        form: MultiValueMap<String, String>,
        step: String,
    ): TokenExchangeTokenResponse {
        val response = try {
            restClientBuilder
                .clone()
                .build()
                .post()
                .uri(tokenEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body<TokenExchangeTokenResponse>()
        } catch (ex: RestClientException) {
            logger.warn(ex) { "Keycloak $step request to $tokenEndpoint failed" }
            throw TokenExchangeException("Could not obtain a JWT token via Keycloak $step: ${ex.message}", ex)
        }

        if (response?.accessToken.isNullOrBlank()) {
            val reason = response?.errorDescription ?: response?.error ?: "no access_token in response"
            throw TokenExchangeException("Keycloak $step at $tokenEndpoint did not return a valid access token: $reason")
        }

        return response
    }

    private fun formOf(vararg params: Pair<String, String?>): MultiValueMap<String, String> {
        val form = LinkedMultiValueMap<String, String>()
        params.forEach { (key, value) -> if (!value.isNullOrBlank()) form.add(key, value) }
        return form
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        private const val GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials"
        private const val GRANT_TYPE_TOKEN_EXCHANGE = "urn:ietf:params:oauth:grant-type:token-exchange"
        private const val SUBJECT_TOKEN_TYPE_ACCESS_TOKEN = "urn:ietf:params:oauth:token-type:access_token"
    }
}

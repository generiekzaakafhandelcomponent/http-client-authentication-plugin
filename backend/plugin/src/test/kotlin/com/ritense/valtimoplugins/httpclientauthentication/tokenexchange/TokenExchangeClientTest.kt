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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.ritense.valtimo.web.logging.LoggingRestClientCustomizer
import com.ritense.valtimoplugins.httpclientauthentication.exception.TokenExchangeException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class TokenExchangeClientTest {
    private val loggers =
        listOf(LoggingRestClientCustomizer::class.java, TokenExchangeClient::class.java)
            .map { LoggerFactory.getLogger(it) as Logger }
    private val logged = ListAppender<ILoggingEvent>()
    private val builder = RestClient.builder().requestInterceptor(LoggingRestClientCustomizer())
    private val server = MockRestServiceServer.bindTo(builder).build()

    @BeforeEach
    fun captureLogs() {
        logged.start()
        loggers.forEach {
            it.level = Level.DEBUG
            it.addAppender(logged)
        }
    }

    @AfterEach
    fun releaseLogs() {
        loggers.forEach {
            it.detachAppender(logged)
            it.level = null
        }
    }

    @Test
    fun `should not let Valtimo's request logging log the client secret or the token`() {
        server
            .expect(requestTo(TOKEN_ENDPOINT))
            .andRespond(withSuccess("""{"access_token":"$TOKEN","expires_in":300}""", MediaType.APPLICATION_JSON))

        val response = TokenExchangeClient(builder).exchangeToken(CONFIG)

        assertEquals(TOKEN, response.accessToken)
        server.verify()
        assertLeaksNothing(logged.list.joinToString("\n") { it.formattedMessage })
    }

    @Test
    fun `should leave the response body out of a token endpoint error`() {
        server
            .expect(requestTo(TOKEN_ENDPOINT))
            .andRespond(
                withStatus(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"error":"invalid_client","client_secret":"$CLIENT_SECRET"}"""),
            )

        val exception = assertThrows<TokenExchangeException> { TokenExchangeClient(builder).exchangeToken(CONFIG) }

        val cause = exception.cause as HttpClientErrorException
        assertEquals(HttpStatus.UNAUTHORIZED, cause.statusCode)
        assertTrue(cause.responseBodyAsString.isEmpty())
        assertLeaksNothing(exception.message.orEmpty())
        assertLeaksNothing(logged.list.joinToString("\n") { "${it.formattedMessage} ${it.throwableProxy?.message}" })
    }

    private fun assertLeaksNothing(text: String) {
        assertFalse(text.contains(CLIENT_SECRET), text)
        assertFalse(text.contains(TOKEN), text)
    }

    companion object {
        private const val TOKEN_ENDPOINT = "https://login.example.com/token"
        private const val CLIENT_SECRET = "the-client-secret"
        private const val TOKEN = "the-access-token"
        private val CONFIG =
            TokenExchangeConfig(
                tokenEndpoint = URI(TOKEN_ENDPOINT),
                clientId = "client",
                clientSecret = CLIENT_SECRET,
                scope = "https://graph.microsoft.com/.default",
            )
    }
}

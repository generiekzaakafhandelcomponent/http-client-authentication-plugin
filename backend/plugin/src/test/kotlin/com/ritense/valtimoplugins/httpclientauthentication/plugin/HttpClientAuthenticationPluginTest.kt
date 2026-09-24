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

import com.ritense.valtimoplugins.httpclientauthentication.model.AuthenticationType
import com.ritense.valtimoplugins.httpclientauthentication.tokenexchange.TokenExchangeClient
import com.ritense.valtimoplugins.httpclientauthentication.tokenexchange.TokenExchangeTokenResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.net.URI
import kotlin.test.assertContains

class HttpClientAuthenticationPluginTest {

    private lateinit var tokenExchangeClient: TokenExchangeClient
    private lateinit var plugin: HttpClientAuthenticationPlugin

    @BeforeEach
    fun setUp() {
        tokenExchangeClient = mock()
        whenever(tokenExchangeClient.exchangeToken(any()))
            .thenReturn(TokenExchangeTokenResponse(accessToken = "exchanged-jwt", expiresIn = 300))

        plugin = HttpClientAuthenticationPlugin(tokenExchangeClient)
    }

    @Test
    fun `applyAuth should add the exchanged JWT as bearer token on every request`() {
        plugin.apply {
            authenticationType = AuthenticationType.TOKEN_EXCHANGE
            tokenEndpoint = URI("https://keycloak.example.com/token")
            clientId = "client"
            clientSecret = "secret"
            audience = "haal-centraal"
        }

        val builder = plugin.applyAuth(RestClient.builder())
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(ExpectedCount.times(2), requestTo(RESOURCE_URL))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer exchanged-jwt"))
            .andRespond(withSuccess())

        val restClient = builder.build()
        repeat(2) { restClient.get().uri(RESOURCE_URL).retrieve().toBodilessEntity() }

        server.verify()
        verify(tokenExchangeClient, times(1)).exchangeToken(any())
    }

    @Test
    fun `applyAuth should add the configured secret as bearer token`() {
        plugin.apply {
            authenticationType = AuthenticationType.BEARER
            authSecret = "static-token"
        }

        val builder = plugin.applyAuth(RestClient.builder())
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(requestTo(RESOURCE_URL))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer static-token"))
            .andRespond(withSuccess())

        builder.build().get().uri(RESOURCE_URL).retrieve().toBodilessEntity()

        server.verify()
        verifyNoInteractions(tokenExchangeClient)
    }

    @Test
    fun `applyAuth should add the configured secret as custom header`() {
        plugin.apply {
            authenticationType = AuthenticationType.HEADER
            authHeaderName = "X-Api-Key"
            authSecret = "api-key"
        }

        val builder = plugin.applyAuth(RestClient.builder())
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(requestTo(RESOURCE_URL))
            .andExpect(header("X-Api-Key", "api-key"))
            .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
            .andRespond(withSuccess())

        builder.build().get().uri(RESOURCE_URL).retrieve().toBodilessEntity()

        server.verify()
        verifyNoInteractions(tokenExchangeClient)
    }

    @Test
    fun `applyAuth should not add any authentication for NONE`() {
        plugin.authenticationType = AuthenticationType.NONE

        val builder = plugin.applyAuth(RestClient.builder())
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(requestTo(RESOURCE_URL))
            .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
            .andRespond(withSuccess())

        builder.build().get().uri(RESOURCE_URL).retrieve().toBodilessEntity()

        server.verify()
        verifyNoInteractions(tokenExchangeClient)
    }

    @Test
    fun `validateProperties should accept complete configurations for every type`() {
        assertDoesNotThrow {
            plugin.apply { authenticationType = AuthenticationType.NONE }.validateProperties()
        }
        assertDoesNotThrow {
            plugin.apply {
                authenticationType = AuthenticationType.BEARER
                authSecret = "secret"
            }.validateProperties()
        }
        assertDoesNotThrow {
            plugin.apply {
                authenticationType = AuthenticationType.HEADER
                authHeaderName = "X-Api-Key"
            }.validateProperties()
        }
        assertDoesNotThrow {
            plugin.apply {
                authenticationType = AuthenticationType.TOKEN_EXCHANGE
                tokenEndpoint = URI("https://keycloak.example.com/token")
                clientId = "client"
                clientSecret = "secret"
                audience = "haal-centraal"
            }.validateProperties()
        }
    }

    @Test
    fun `validateProperties should reject BEARER without authSecret`() {
        plugin.authenticationType = AuthenticationType.BEARER

        val exception = assertThrows<IllegalArgumentException> { plugin.validateProperties() }
        assertContains(exception.message!!, "authSecret")
    }

    @Test
    fun `validateProperties should reject HEADER without authHeaderName`() {
        plugin.apply {
            authenticationType = AuthenticationType.HEADER
            authSecret = "secret"
        }

        val exception = assertThrows<IllegalArgumentException> { plugin.validateProperties() }
        assertContains(exception.message!!, "authHeaderName")
    }

    @Test
    fun `validateProperties should list every missing TOKEN_EXCHANGE property`() {
        plugin.apply {
            authenticationType = AuthenticationType.TOKEN_EXCHANGE
            clientId = "client"
        }

        val exception = assertThrows<IllegalArgumentException> { plugin.validateProperties() }
        assertContains(exception.message!!, "tokenEndpoint")
        assertContains(exception.message!!, "clientSecret")
        assertContains(exception.message!!, "audience")
    }

    @Test
    fun `validateProperties should reject a keystorePath without keystoreSecret`() {
        plugin.apply {
            authenticationType = AuthenticationType.NONE
            keystorePath = "/certs/client.jks"
        }

        val exception = assertThrows<IllegalArgumentException> { plugin.validateProperties() }
        assertContains(exception.message!!, "keystoreSecret")
    }

    companion object {
        private const val RESOURCE_URL = "https://api.example.com/resource"
    }
}

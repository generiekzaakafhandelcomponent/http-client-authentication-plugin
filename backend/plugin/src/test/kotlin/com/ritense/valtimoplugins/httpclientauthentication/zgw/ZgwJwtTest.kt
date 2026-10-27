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

package com.ritense.valtimoplugins.httpclientauthentication.zgw

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.ritense.openzaak.plugin.token.ValtimoOpenZaakPluginTokenGeneratorService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals

internal class ZgwJwtTest {
    private val objectMapper = ObjectMapper()
    private val valtimoTokenGenerator = ValtimoOpenZaakPluginTokenGeneratorService()

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `should make the same claims as Valtimo's ZGW token without a user`() {
        assertSameClaimsAsValtimo()
    }

    @Test
    fun `should make the same claims as Valtimo's ZGW token for a user with roles`() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(
                "jan.jansen",
                null,
                listOf(SimpleGrantedAuthority("ROLE_USER"), SimpleGrantedAuthority("ROLE_ADMIN")),
            )

        val claims = assertSameClaimsAsValtimo()

        assertEquals("jan.jansen", claims["user_id"].asText())
        assertEquals(listOf("ROLE_USER", "ROLE_ADMIN"), claims["roles"].map { it.asText() })
    }

    @Test
    fun `should sign with HS256 and the secret`() {
        val token = ZgwJwt.generate(CLIENT_ID, SECRET)

        val (header, payload, signature) = token.split(".")
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(SECRET.toByteArray(), "HmacSHA256")) }
        assertEquals(
            Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal("$header.$payload".toByteArray())),
            signature,
        )
        assertEquals("HS256", decode(header)["alg"].asText())
    }

    private fun assertSameClaimsAsValtimo(): JsonNode {
        val expected = decode(valtimoTokenGenerator.generateToken(SECRET, CLIENT_ID).split(".")[1]) as ObjectNode
        val actual = decode(ZgwJwt.generate(CLIENT_ID, SECRET).split(".")[1]) as ObjectNode

        // Made within the same few milliseconds, but possibly in another second
        assertEquals(expected["iat"].asLong().toDouble(), actual["iat"].asLong().toDouble(), 1.0)
        assertEquals(expected.without<ObjectNode>("iat"), actual.deepCopy().without<ObjectNode>("iat"))
        return actual
    }

    private fun decode(part: String) = objectMapper.readTree(Base64.getUrlDecoder().decode(part))

    companion object {
        private const val CLIENT_ID = "valtimo_client"
        private const val SECRET = "a-client-secret-of-at-least-32-characters"
    }
}

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

package com.ritense.valtimoplugins.httpclientauthentication.zgw

import com.fasterxml.jackson.databind.ObjectMapper
import com.ritense.valtimo.contract.utils.SecurityUtils
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The JWT the ZGW APIs (Open Zaak, Open Notificaties, ...) expect: HS256, signed with the client secret, with the same
 * claims as Valtimo's `openzaak-plugin-authentication`. Made here so the plugin doesn't depend on that module.
 */
object ZgwJwt {
    const val MINIMUM_SECRET_LENGTH = 32

    private const val ALGORITHM = "HmacSHA256"
    private const val FALLBACK_USER = "Valtimo"
    private val objectMapper = ObjectMapper()
    private val base64 = Base64.getUrlEncoder().withoutPadding()

    fun generate(
        clientId: String,
        secret: String,
    ): String {
        val claims =
            linkedMapOf<String, Any>(
                "iss" to clientId,
                "iat" to Instant.now().epochSecond,
                "client_id" to clientId,
            )
        claims.putAll(userClaims())

        val header = base64.encodeToString(objectMapper.writeValueAsBytes(mapOf("alg" to "HS256", "typ" to "JWT")))
        val payload = base64.encodeToString(objectMapper.writeValueAsBytes(claims))
        val mac = Mac.getInstance(ALGORITHM).apply { init(SecretKeySpec(secret.toByteArray(), ALGORITHM)) }
        val signature = base64.encodeToString(mac.doFinal("$header.$payload".toByteArray()))
        return "$header.$payload.$signature"
    }

    private fun userClaims(): Map<String, Any> {
        val authenticated = SecurityUtils.isAuthenticated()
        val roles = if (authenticated) SecurityUtils.getCurrentUserRoles() else emptyList()
        return buildMap {
            put("user_id", (if (authenticated) SecurityUtils.getCurrentUserLogin() else null) ?: FALLBACK_USER)
            put("user_representation", "")
            if (roles.isNotEmpty()) {
                put("roles", roles)
            }
        }
    }
}

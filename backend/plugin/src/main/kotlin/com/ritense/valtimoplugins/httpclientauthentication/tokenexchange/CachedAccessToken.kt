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

import java.time.Instant

/**
 * Holds an exchanged access token together with the moment it was issued, so it can be reused
 * until shortly before Keycloak considers it expired.
 */
class CachedAccessToken(
    val accessToken: String,
    expiresInSeconds: Long?,
    private val issuedAt: Instant = Instant.now(),
) {
    // Refresh a little early to avoid using a token that expires mid-flight, but never by more
    // than half the TTL, so a short-lived token doesn't end up expired the instant it's cached.
    private val expiresAt: Instant = run {
        val ttlSeconds = expiresInSeconds ?: DEFAULT_TTL_SECONDS
        val marginSeconds = minOf(EXPIRY_MARGIN_SECONDS, ttlSeconds / 2)
        issuedAt.plusSeconds(ttlSeconds - marginSeconds)
    }

    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)

    companion object {
        private const val DEFAULT_TTL_SECONDS = 60L
        private const val EXPIRY_MARGIN_SECONDS = 5L
    }
}

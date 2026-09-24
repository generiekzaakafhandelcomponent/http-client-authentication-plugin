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

import java.io.FileInputStream
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * Builds an [SSLContext] for a plugin to present a client certificate (mTLS) from a JKS keystore
 * file on disk.
 */
object MtlsContextFactory {

    fun createFromKeystore(
        keystorePath: String,
        keystoreSecret: String,
        truststorePath: String?,
        truststoreSecret: String?,
    ): SSLContext {
        val keyStore = KeyStore.getInstance("jks").apply {
            FileInputStream(keystorePath).use { load(it, keystoreSecret.toCharArray()) }
        }
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, keystoreSecret.toCharArray())
        }

        val trustManagers = truststorePath?.let { path ->
            require(!truststoreSecret.isNullOrBlank()) {
                "truststoreSecret is required when truststorePath is configured"
            }
            val trustStore = KeyStore.getInstance("jks").apply {
                FileInputStream(path).use { load(it, truststoreSecret.toCharArray()) }
            }
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(trustStore)
            }.trustManagers
        }

        return SSLContext.getInstance("TLS").apply {
            init(keyManagerFactory.keyManagers, trustManagers, null)
        }
    }
}

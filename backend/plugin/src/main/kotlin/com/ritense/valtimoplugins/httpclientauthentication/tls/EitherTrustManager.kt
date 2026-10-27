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

import java.net.Socket
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager

/**
 * Trusts a server that [first] or, failing that, [second] trusts. Only used for the server side: a client certificate
 * is never checked with it.
 */
internal class EitherTrustManager(
    private val first: X509ExtendedTrustManager,
    private val second: X509ExtendedTrustManager,
) : X509ExtendedTrustManager() {
    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        socket: Socket?,
    ) = either { it.checkServerTrusted(chain, authType, socket) }

    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        engine: SSLEngine?,
    ) = either { it.checkServerTrusted(chain, authType, engine) }

    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
    ) = either { it.checkServerTrusted(chain, authType) }

    override fun checkClientTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        socket: Socket?,
    ): Unit = throw CertificateException("Client certificates are not trusted")

    override fun checkClientTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        engine: SSLEngine?,
    ): Unit = throw CertificateException("Client certificates are not trusted")

    override fun checkClientTrusted(
        chain: Array<X509Certificate>,
        authType: String,
    ): Unit = throw CertificateException("Client certificates are not trusted")

    override fun getAcceptedIssuers(): Array<X509Certificate> = first.acceptedIssuers + second.acceptedIssuers

    private fun either(check: (X509ExtendedTrustManager) -> Unit) {
        try {
            check(first)
        } catch (firstFailure: CertificateException) {
            try {
                check(second)
            } catch (secondFailure: CertificateException) {
                firstFailure.addSuppressed(secondFailure)
                throw firstFailure
            }
        }
    }
}

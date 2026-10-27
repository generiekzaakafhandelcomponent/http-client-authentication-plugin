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

import com.ritense.valtimoplugins.httpclientauthentication.sha256Hex
import org.springframework.boot.ssl.SslBundle
import org.springframework.boot.ssl.pem.PemContent
import org.springframework.boot.ssl.pem.PemSslStore
import org.springframework.boot.ssl.pem.PemSslStoreBundle
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.UnrecoverableKeyException
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager

/**
 * The certificates of a configuration, either as PEM text or as keystore files. As PEM: the client certificate in
 * `clientCertificate` and `clientPrivateKey`, the trusted server certificates in `serverCertificate`. As files: the client
 * certificate in `keystorePath` and `keystoreSecret`, the trusted server certificates in `truststorePath` and
 * `truststoreSecret`.
 *
 * The non-secret property of a source switches it on. Valtimo keeps a stored secret when an update leaves it empty, so a
 * secret whose source is off is ignored.
 */
class TlsMaterial(
    clientCertificate: String? = null,
    clientPrivateKey: String? = null,
    serverCertificate: String? = null,
    keystorePath: String? = null,
    keystoreSecret: String? = null,
    truststorePath: String? = null,
    truststoreSecret: String? = null,
) {
    private val clientCertificate = clientCertificate.orNullIfBlank()
    private val clientPrivateKey = clientPrivateKey.orNullIfBlank()?.takeIf { this.clientCertificate != null }
    private val serverCertificate = serverCertificate.orNullIfBlank()
    private val keystorePath = keystorePath.orNullIfBlank()
    private val keystoreSecret = keystoreSecret.orNullIfBlank()?.takeIf { this.keystorePath != null }
    private val truststorePath = truststorePath.orNullIfBlank()
    private val truststoreSecret = truststoreSecret.orNullIfBlank()?.takeIf { this.truststorePath != null }

    val isConfigured: Boolean
        get() = clientCertificate != null || keystorePath != null || serverCertificate != null || truststorePath != null

    /**
     * Identifies the material: changes when a property changes, and when a keystore file is replaced, so it is computed
     * on every use (a client built once still notices a replaced file).
     */
    val digest: String
        get() =
            sha256Hex(
                clientCertificate,
                clientPrivateKey,
                serverCertificate,
                keystorePath,
                keystoreSecret,
                fileVersion(keystorePath),
                truststorePath,
                truststoreSecret,
                fileVersion(truststorePath),
            )

    /**
     * The problems that can be found without reading files, so a configuration is judged the same on every node.
     */
    fun problems(): List<String> =
        buildList {
            // One source for all certificates: the screen offers one choice, and saving it drops the other source
            if ((clientCertificate != null || serverCertificate != null) &&
                (keystorePath != null || truststorePath != null)
            ) {
                add(
                    "Configure the certificates either as PEM ($CLIENT_CERTIFICATE, $SERVER_CERTIFICATE) or as " +
                        "keystore files ($KEYSTORE_PATH, $TRUSTSTORE_PATH), not both",
                )
            }
            if (keystorePath != null && keystoreSecret == null) {
                add("$KEYSTORE_SECRET is required when $KEYSTORE_PATH is configured")
            }
            if (truststorePath != null && truststoreSecret == null) {
                add("$TRUSTSTORE_SECRET is required when $TRUSTSTORE_PATH is configured")
            }
            problemOf { pemKeyStore() }?.let(::add)
            problemOf { pemTrustStore() }?.let(::add)
        }

    /**
     * The SSL context for the API calls: the configured server certificates replace the certificates the JVM trusts.
     */
    fun sslContext(): SSLContext {
        requireValid()
        return sslContext(trustManagers())
    }

    /**
     * The SSL context for calls that should also reach servers the JVM trusts, such as the token endpoint: the
     * configured server certificates are trusted next to [defaultTrustManager].
     */
    fun sslContextAlsoTrusting(defaultTrustManager: X509ExtendedTrustManager): SSLContext {
        requireValid()
        val configured = trustManagers()?.filterIsInstance<X509ExtendedTrustManager>()?.firstOrNull()
        val trustManager = configured?.let { EitherTrustManager(it, defaultTrustManager) } ?: defaultTrustManager
        return sslContext(arrayOf(trustManager))
    }

    /**
     * The key and trust managers of [sslContext], for clients that take them instead of an SSL context (CXF, netty).
     * Null means the JVM's default.
     */
    fun managers(): TlsManagers {
        requireValid()
        return TlsManagers(keyManagers(), trustManagers())
    }

    private fun requireValid() {
        problems().firstOrNull()?.let { throw IllegalArgumentException(it) }
    }

    private fun sslContext(trustManagers: Array<TrustManager>?): SSLContext =
        SSLContext.getInstance("TLS").apply { init(keyManagers(), trustManagers, null) }

    private fun keyManagers(): Array<KeyManager>? {
        pemKeyStore()?.let { return SslBundle.of(PemSslStoreBundle(it, null)).managers.keyManagers }
        val keyStore = fileKeyStore() ?: return null
        return KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).run {
            try {
                init(keyStore, requireNotNull(keystoreSecret).toCharArray())
            } catch (e: UnrecoverableKeyException) {
                throw IllegalArgumentException("Property '$KEYSTORE_SECRET' does not open the key in '$KEYSTORE_PATH'")
            }
            keyManagers
        }
    }

    private fun trustManagers(): Array<TrustManager>? {
        pemTrustStore()?.let { return SslBundle.of(PemSslStoreBundle(null, it)).managers.trustManagers }
        val trustStore = fileTrustStore() ?: return null
        return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).run {
            init(trustStore)
            trustManagers
        }
    }

    private fun pemKeyStore(): PemSslStore? =
        clientCertificate?.let { certificateText ->
            val keyText =
                clientPrivateKey
                    ?: throw IllegalArgumentException(
                        "Property '$CLIENT_PRIVATE_KEY' is required when '$CLIENT_CERTIFICATE' is set",
                    )
            val certificates = readCertificates(certificateText, CLIENT_CERTIFICATE)
            val privateKey = readPrivateKey(keyText)
            require(keyMatchesCertificate(privateKey, certificates.first())) {
                "Property '$CLIENT_PRIVATE_KEY' does not belong to the certificate in '$CLIENT_CERTIFICATE'"
            }
            PemSslStore.of(certificates, privateKey)
        }

    private fun pemTrustStore(): PemSslStore? =
        serverCertificate?.let { PemSslStore.of(readCertificates(it, SERVER_CERTIFICATE), null) }

    private fun fileKeyStore(): KeyStore? =
        keystorePath?.let {
            loadKeyStore(it, keystoreSecret, KEYSTORE_PATH, KEYSTORE_SECRET)
        }

    private fun fileTrustStore(): KeyStore? =
        truststorePath?.let { loadKeyStore(it, truststoreSecret, TRUSTSTORE_PATH, TRUSTSTORE_SECRET) }

    // The JKS keystore type reads PKCS12 files too
    private fun loadKeyStore(
        path: String,
        secret: String?,
        pathProperty: String,
        secretProperty: String,
    ): KeyStore {
        val keyStore = KeyStore.getInstance("JKS")
        try {
            Files.newInputStream(Path.of(path)).use { keyStore.load(it, requireNotNull(secret).toCharArray()) }
        } catch (e: IOException) {
            if (e.cause is UnrecoverableKeyException) {
                throw IllegalArgumentException(
                    "Property '$secretProperty' does not open the keystore in '$pathProperty'",
                )
            }
            throw IllegalArgumentException(
                "Property '$pathProperty' does not point to a readable keystore (${e.javaClass.simpleName})",
                e,
            )
        }
        return keyStore
    }

    private fun readCertificates(
        text: String,
        property: String,
    ): List<X509Certificate> {
        val certificates = runCatching { PemContent.of(text.trim()).certificates }.getOrNull()
        require(!certificates.isNullOrEmpty()) { "Property '$property' does not contain a valid PEM certificate" }
        // The property isn't secret, so a key in it would be stored unencrypted
        require(!text.contains("PRIVATE KEY-----")) { "Property '$property' must not contain a private key" }
        return certificates
    }

    private fun readPrivateKey(text: String): PrivateKey {
        val privateKey = runCatching { PemContent.of(text.trim()).privateKey }.getOrNull()
        return requireNotNull(
            privateKey,
        ) { "Property '$CLIENT_PRIVATE_KEY' does not contain a valid unencrypted PEM private key" }
    }

    private fun keyMatchesCertificate(
        privateKey: PrivateKey,
        certificate: X509Certificate,
    ): Boolean {
        val publicKey = certificate.publicKey
        if (privateKey.algorithm != publicKey.algorithm) {
            return false
        }
        // Covers RSA and RSASSA-PSS, whose signatures cannot be made without parameters
        if (privateKey is RSAPrivateKey && publicKey is RSAPublicKey) {
            return privateKey.modulus == publicKey.modulus
        }
        val algorithm = if (privateKey.algorithm == "EC") "SHA256withECDSA" else privateKey.algorithm
        val challenge = "http-client-authentication".toByteArray()
        return try {
            val signed =
                Signature.getInstance(algorithm).run {
                    initSign(privateKey)
                    update(challenge)
                    sign()
                }
            Signature.getInstance(algorithm).run {
                initVerify(publicKey)
                update(challenge)
                verify(signed)
            }
        } catch (e: GeneralSecurityException) {
            // E.g. an algorithm the JVM can't sign with, or keys of the same family but another curve
            false
        }
    }

    private fun problemOf(check: () -> Unit): String? =
        try {
            check()
            null
        } catch (e: IllegalArgumentException) {
            e.message
        }

    // Size and modification time, so a replaced file gets a new digest without reading it
    private fun fileVersion(path: String?): String? =
        path?.let {
            try {
                val file = Path.of(it)
                "${Files.size(file)}:${Files.getLastModifiedTime(file).toMillis()}"
            } catch (e: Exception) {
                "unreadable"
            }
        }

    private fun String?.orNullIfBlank() = this?.takeIf { it.isNotBlank() }

    companion object {
        private const val CLIENT_CERTIFICATE = "clientCertificate"
        private const val CLIENT_PRIVATE_KEY = "clientPrivateKey"
        private const val SERVER_CERTIFICATE = "serverCertificate"
        private const val KEYSTORE_PATH = "keystorePath"
        private const val KEYSTORE_SECRET = "keystoreSecret"
        private const val TRUSTSTORE_PATH = "truststorePath"
        private const val TRUSTSTORE_SECRET = "truststoreSecret"
    }
}

class TlsManagers(
    val keyManagers: Array<KeyManager>?,
    val trustManagers: Array<TrustManager>?,
)

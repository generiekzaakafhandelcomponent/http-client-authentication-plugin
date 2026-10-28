package com.ritense.valtimoplugins.httpclientauthentication

import com.ritense.plugin.annotation.PluginCategory
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient
import javax.net.ssl.SSLContext

@PluginCategory("http-client-authentication")
interface HttpClientAuthenticator {
    fun applyAuth(builder: RestClient.Builder): RestClient.Builder?

    /**
     * The credentials for one request, for a caller that sends it over its own connection (its own proxy, timeouts and
     * redirect handling). Ask per request: a token is fetched or renewed when needed, which blocks.
     */
    fun authenticationHeaders(): HttpHeaders

    /**
     * The certificates for the caller's own connection: the client certificate and the trusted server certificate, or
     * null when none are configured. Null for the SAML token service, whose certificates are for the token service only.
     */
    fun sslContext(): SSLContext?
}

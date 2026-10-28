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

package com.ritense.valtimoplugins.httpclientauthentication.cxf

import com.ritense.valtimoplugins.httpclientauthentication.model.AuthenticationType
import com.ritense.valtimoplugins.httpclientauthentication.plugin.HttpClientAuthenticationPluginBase
import org.apache.cxf.configuration.jsse.TLSClientParameters
import org.apache.cxf.endpoint.Client
import org.apache.cxf.message.Message
import org.apache.cxf.phase.AbstractPhaseInterceptor
import org.apache.cxf.phase.Phase
import org.apache.cxf.transport.http.HTTPConduit
import java.util.TreeMap

/** Authentication of CXF (SOAP) clients. Only loaded when CXF is on the classpath. */
object CxfAuthenticator {
    fun applyAuth(
        plugin: HttpClientAuthenticationPluginBase,
        client: Client,
    ) {
        plugin.requireAuthentication()
        plugin.apiTlsManagers()?.let { tlsManagers ->
            val conduit =
                requireNotNull(client.conduit as? HTTPConduit) {
                    "Certificates need an HTTP conduit, not ${client.conduit.javaClass.name}"
                }
            // A copy: the conduit's parameters may be shared
            conduit.tlsClientParameters =
                copyOf(conduit.tlsClientParameters).apply {
                    keyManagers = tlsManagers.keyManagers
                    trustManagers = tlsManagers.trustManagers
                }
        }
        client.outInterceptors.removeIf { it is AuthenticationHeaderInterceptor }
        if (plugin.authenticationType != AuthenticationType.NONE) {
            client.outInterceptors.add(AuthenticationHeaderInterceptor(plugin))
        }
    }

    private fun copyOf(parameters: TLSClientParameters?): TLSClientParameters {
        val copy = TLSClientParameters()
        if (parameters != null) {
            copy.isDisableCNCheck = parameters.isDisableCNCheck
            copy.hostnameVerifier = parameters.hostnameVerifier
            copy.secureSocketProtocol = parameters.secureSocketProtocol
            copy.cipherSuites = parameters.cipherSuites
            copy.cipherSuitesFilter = parameters.cipherSuitesFilter
            copy.certConstraints = parameters.certConstraints
            copy.certAlias = parameters.certAlias
            copy.jsseProvider = parameters.jsseProvider
            copy.secureRandom = parameters.secureRandom
            copy.sslCacheTimeout = parameters.sslCacheTimeout
        }
        return copy
    }

    /**
     * Sets the headers on every message, so a token is fresh. Before the stream: the conduit reads the headers on the
     * first write. The headers already there (e.g. the consumer's `Expect`) are kept, in a mutable copy.
     */
    internal class AuthenticationHeaderInterceptor(
        private val plugin: HttpClientAuthenticationPluginBase,
    ) : AbstractPhaseInterceptor<Message>(Phase.PRE_STREAM) {
        override fun handleMessage(message: Message) {
            val headers = TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER)
            @Suppress("UNCHECKED_CAST")
            (message[Message.PROTOCOL_HEADERS] as? Map<String, List<String>>)?.let(headers::putAll)
            headers.putAll(plugin.authenticationHeaders())
            message[Message.PROTOCOL_HEADERS] = headers
        }
    }
}

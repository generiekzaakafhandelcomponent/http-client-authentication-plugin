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

package com.ritense.valtimoplugins.httpclientauthentication.reactor

import com.ritense.valtimoplugins.httpclientauthentication.model.AuthenticationType
import com.ritense.valtimoplugins.httpclientauthentication.plugin.HttpClientAuthenticationPluginBase
import io.netty.channel.ChannelOption
import io.netty.handler.ssl.ClientAuth
import io.netty.handler.ssl.JdkSslContext
import reactor.netty.http.client.HttpClient

/** Reactor-netty clients, for the Haal Centraal interfaces. Only loaded when reactor-netty is on the classpath. */
object ReactorNettyClients {
    fun authenticated(plugin: HttpClientAuthenticationPluginBase): HttpClient {
        plugin.requireAuthentication()
        var client =
            HttpClient
                .create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, plugin.connectionTimeoutDuration().toMillis().toInt())
                .responseTimeout(plugin.responseTimeoutDuration())
        // The same SSL context as the plugin's other clients
        plugin.apiSslContext()?.let { sslContext ->
            client = client.secure { it.sslContext(JdkSslContext(sslContext, true, ClientAuth.NONE)) }
        }
        if (plugin.authenticationType != AuthenticationType.NONE) {
            client =
                client.headersWhen { headers ->
                    plugin.authenticationHeadersMono().map { values ->
                        headers.apply { values.forEach { (name, value) -> set(name, value) } }
                    }
                }
        }
        return client
    }
}

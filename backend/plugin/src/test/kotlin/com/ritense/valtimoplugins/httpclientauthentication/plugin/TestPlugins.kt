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

import com.ritense.plugin.domain.PluginConfigurationId
import com.ritense.valtimo.contract.client.ValtimoHttpRestClientConfigurationProperties
import com.ritense.valtimoplugins.httpclientauthentication.model.AuthenticationType
import com.ritense.valtimoplugins.httpclientauthentication.saml.SamlTokenClient
import com.ritense.valtimoplugins.httpclientauthentication.tls.TlsFixtures
import com.ritense.valtimoplugins.httpclientauthentication.tls.TlsRequestFactoryCache
import com.ritense.valtimoplugins.httpclientauthentication.tokenexchange.TokenExchangeTokenCache
import org.mockito.kotlin.mock
import org.springframework.web.client.RestClient

/** An instance of a generated plugin class, as Valtimo gets it from the factory, without properties. */
internal fun newPlugin(
    tlsRequestFactoryCache: TlsRequestFactoryCache =
        TlsRequestFactoryCache(ValtimoHttpRestClientConfigurationProperties()),
    tokenExchangeTokenCache: TokenExchangeTokenCache = TokenExchangeTokenCache(mock()),
    samlTokenClient: SamlTokenClient = SamlTokenClient(RestClient.builder()),
    pluginClass: Class<out HttpClientAuthenticationPluginBase> = GeneratedPluginClass.get(),
): HttpClientAuthenticationPluginBase =
    PluginClassGenerator
        .constructorOf(pluginClass)
        .newInstance(PluginConfigurationId.newId(), tlsRequestFactoryCache, tokenExchangeTokenCache, samlTokenClient)

/** A configured instance with its own caches. Not apply: the plugin's own apply(ExchangeFunction) would win. */
internal fun configuredPlugin(
    type: AuthenticationType,
    tlsRequestFactoryCache: TlsRequestFactoryCache =
        TlsRequestFactoryCache(ValtimoHttpRestClientConfigurationProperties()),
    configure: HttpClientAuthenticationPluginBase.() -> Unit = {},
): HttpClientAuthenticationPluginBase =
    newPlugin(tlsRequestFactoryCache).also {
        it.authenticationType = type
        it.configure()
    }

/** Presents the test client certificate and trusts the test CA. */
internal fun configuredPluginWithCertificates(
    type: AuthenticationType,
    tlsRequestFactoryCache: TlsRequestFactoryCache =
        TlsRequestFactoryCache(ValtimoHttpRestClientConfigurationProperties()),
    configure: HttpClientAuthenticationPluginBase.() -> Unit = {},
): HttpClientAuthenticationPluginBase =
    configuredPlugin(type, tlsRequestFactoryCache) {
        clientCertificate = TlsFixtures.CLIENT_CERTIFICATE
        clientPrivateKey = TlsFixtures.CLIENT_PRIVATE_KEY
        serverCertificate = TlsFixtures.CA_CERTIFICATE
        configure()
    }

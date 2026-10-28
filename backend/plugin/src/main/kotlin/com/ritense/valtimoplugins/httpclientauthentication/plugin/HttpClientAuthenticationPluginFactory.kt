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

import com.ritense.plugin.PluginFactory
import com.ritense.plugin.service.PluginService
import com.ritense.valtimoplugins.httpclientauthentication.saml.SamlTokenClient
import com.ritense.valtimoplugins.httpclientauthentication.tls.TlsRequestFactoryCache
import com.ritense.valtimoplugins.httpclientauthentication.tokenexchange.TokenExchangeTokenCache

class HttpClientAuthenticationPluginFactory(
    pluginService: PluginService,
    private val tlsRequestFactoryCache: TlsRequestFactoryCache,
    private val tokenExchangeTokenCache: TokenExchangeTokenCache,
    private val samlTokenClient: SamlTokenClient,
    // Generated when the context starts: Valtimo loads the class by name outside this factory too
    pluginClass: Class<out HttpClientAuthenticationPluginBase> = GeneratedPluginClass.get(),
) : PluginFactory<HttpClientAuthenticationPluginBase>(pluginService) {
    private val constructor = PluginClassGenerator.constructorOf(pluginClass)

    override fun create(): HttpClientAuthenticationPluginBase =
        constructor.newInstance(pluginConfigurationId, tlsRequestFactoryCache, tokenExchangeTokenCache, samlTokenClient)
}

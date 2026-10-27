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

package com.ritense.valtimoplugins.httpclientauthentication.support

import com.ritense.valtimoplugins.haalcentraalauthenticationplugin.HaalCentraalAuthentication
import com.ritense.valtimoplugins.httpclientauthentication.plugin.HttpClientAuthenticationPluginBase
import com.ritense.valtimoplugins.httpclientauthentication.reactor.ReactorNettyClients
import reactor.netty.http.client.HttpClient

/**
 * Category `haal-centraal-authentication-plugin` (`haal-centraal-authentication` in 1.0.1-V12), of the SAML plugin.
 * `applyAuth` and `filter` come from the base.
 */
interface HaalCentraalAuthenticationPluginSupport : HaalCentraalAuthentication {
    override fun getAuthenticatedHttpClient(): HttpClient =
        ReactorNettyClients.authenticated(this as HttpClientAuthenticationPluginBase)
}

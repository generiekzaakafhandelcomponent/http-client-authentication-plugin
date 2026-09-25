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

import {PluginSpecification} from '@valtimo/plugin';
import {HTTP_CLIENT_AUTHENTICATION_PLUGIN_LOGO_BASE64} from './assets';
import {
    HttpClientAuthenticationPluginConfigurationComponent
} from "./components/http-client-authentication-plugin-configuration.component";

const httpClientAuthenticationPluginSpecification: PluginSpecification = {
    /*
    The plugin definition key of the plugin.
    This needs to be the same as the id received from the back-end
     */
    pluginId: 'http-client-authentication',
    /*
    A component of the interface PluginConfigurationComponent, used to configure the plugin itself.
     */
    pluginConfigurationComponent: HttpClientAuthenticationPluginConfigurationComponent,
    // Points to a Base64 encoded string, which contains the logo of the plugin.
    pluginLogoBase64: HTTP_CLIENT_AUTHENTICATION_PLUGIN_LOGO_BASE64,
    /*
    For each language key an implementation supports, translation keys with a translation are provided below.
    These can then be used in configuration components using the pluginTranslate pipe or the PluginTranslationService.
    At a minimum, the keys 'title' and 'description' need to be defined.
     */
    pluginTranslations: {
        nl: {
            configurationTitle: 'Configuratienaam',
            configurationTitleTooltip:
                'Http Client Authentication',
            title: 'Http Client Authentication',
            description: 'Biedt authenticatie headers voor uitgaande REST clients',
            authenticationType: 'Authenticatie type',
            authHeaderName: 'Authenticatie header naam',
            authSecret: 'Authenticatie secret',
            tokenEndpoint: 'Token endpoint URL',
            clientId: 'Client ID',
            clientSecret: 'Client secret',
            audience: 'Audience',
            scope: 'Scope',
            mtlsSection: 'mTLS (optioneel)',
            keystorePath: 'Keystore pad (JKS)',
            keystoreSecret: 'Keystore wachtwoord',
            truststorePath: 'Truststore pad (JKS, optioneel)',
            truststoreSecret: 'Truststore wachtwoord',
        },
        en: {
            configurationTitle: 'Configuration name',
            configurationTitleTooltip:
                'Http Client Authentication',
            title: 'Http Client Authentication',
            description: 'Provides authentication headers for outbound REST clients',
            authenticationType: 'Authentication type',
            authHeaderName: 'Authentication header name',
            authSecret: 'Authentication secret',
            tokenEndpoint: 'Token endpoint URL',
            clientId: 'Client ID',
            clientSecret: 'Client secret',
            audience: 'Audience',
            scope: 'Scope',
            mtlsSection: 'mTLS (optional)',
            keystorePath: 'Keystore path (JKS)',
            keystoreSecret: 'Keystore password',
            truststorePath: 'Truststore path (JKS, optional)',
            truststoreSecret: 'Truststore password',
        },
        de: {
            configurationTitle: 'Konfigurationsname',
            configurationTitleTooltip:
                'Http Client Authentication',
            title: 'Http Client Authentication',
            description: 'Stellt Authentifizierungs-Header für ausgehende REST-Clients bereit',
            authenticationType: 'Authentifizierungstyp',
            authHeaderName: 'Name des Authentifizierungs-Headers',
            authSecret: 'Authentifizierungs-Secret',
            tokenEndpoint: 'Token-Endpoint-URL',
            clientId: 'Client-ID',
            clientSecret: 'Client-Secret',
            audience: 'Audience',
            scope: 'Scope',
            mtlsSection: 'mTLS (optional)',
            keystorePath: 'Keystore-Pfad (JKS)',
            keystoreSecret: 'Keystore-Passwort',
            truststorePath: 'Truststore-Pfad (JKS, optional)',
            truststoreSecret: 'Truststore-Passwort',
        }
    }
};

export {httpClientAuthenticationPluginSpecification};

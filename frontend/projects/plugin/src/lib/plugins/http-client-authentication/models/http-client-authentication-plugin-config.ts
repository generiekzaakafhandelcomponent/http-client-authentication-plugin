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

import {PluginConfigurationData} from '@valtimo/plugin';

interface HttpClientAuthenticationPluginConfig extends PluginConfigurationData {
    authenticationType: string;
    authHeaderName?: string;
    authSecret?: string;
    username?: string;
    clientId?: string;
    clientSecret?: string;
    tokenEndpoint?: string;
    audience?: string;
    scope?: string;
    // Milliseconds; the form gives text, the saved configuration a number
    connectionTimeout?: string | number | null;
    responseTimeout?: string | number | null;
    clientCertificate?: string;
    clientPrivateKey?: string;
    serverCertificate?: string;
    keystorePath?: string;
    keystoreSecret?: string;
    truststorePath?: string;
    truststoreSecret?: string;
}

export {HttpClientAuthenticationPluginConfig};

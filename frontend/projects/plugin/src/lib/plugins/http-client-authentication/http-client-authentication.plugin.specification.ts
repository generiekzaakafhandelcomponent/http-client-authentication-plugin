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
            configurationTitlePlaceholder: 'Client Authentication',
            configurationTitleTooltip:
                'Http Client Authentication',
            title: 'Http Client Authentication',
            description: 'Biedt authenticatie en mutual TLS voor uitgaande REST- en SOAP-clients, waaronder de ZGW API, Haal Centraal en Suwinet plugins',
            authenticationType: 'Authenticatie type',
            authHeaderName: 'Authenticatie header naam',
            authHeaderNamePlaceholder: 'X-Api-Key',
            authSecret: 'Authenticatie secret',
            clientId: 'Client ID',
            clientIdPlaceholder: 'valtimo_client',
            clientSecret: 'Client secret',
            clientSecretTooltip: 'Minimaal 32 tekens. Wordt gebruikt om het ZGW JWT-token te ondertekenen.',
            tokenEndpoint: 'Token endpoint URL',
            tokenEndpointPlaceholder: 'https://keycloak.example.com/realms/example/protocol/openid-connect/token',
            tokenEndpointTooltip:
                'Het token endpoint van Keycloak. GZAC haalt hier met client credentials een token op en wisselt dat in voor een token voor de audience.',
            audience: 'Audience',
            audiencePlaceholder: 'haal-centraal',
            audienceTooltip: 'De client in Keycloak waarvoor het token wordt ingewisseld, meestal die van de API.',
            scope: 'Scope',
            scopePlaceholder: 'openid',
            scopeTooltip: 'Optioneel. Wordt meegestuurd bij het ophalen van het eerste token.',
            username: 'Gebruikersnaam',
            usernamePlaceholder: 'valtimo',
            password: 'Wachtwoord',
            samlTokenServiceUrl: 'URL van de SAML token service',
            samlTokenServiceUrlPlaceholder: 'https://sts.example.com/sts/requestsecuritytoken/v1?b64=true',
            samlTokenServiceUrlTooltip:
                'De volledige URL van de WS-Trust token service, met ?b64=true zodat het token als één regel terugkomt. De certificaten hieronder gelden alleen voor deze service, niet voor de API.',
            samlHeaderName: 'Header voor het token (optioneel)',
            samlHeaderNamePlaceholder: 'x-saml-attribute-token1',
            samlHeaderNameTooltip: 'Optioneel. Leeg laten stuurt het token als Authorization: Bearer.',
            connectionTimeout: 'Connectie-timeout (ms, optioneel)',
            responseTimeout: 'Response-timeout (ms, optioneel)',
            timeoutPlaceholder: '10000',
            timeoutTooltip: 'Optioneel, in milliseconden; standaard 10000. Voor de token service en de reactor-netty client van Haal Centraal.',
            tlsSource: 'Certificaten als',
            keystorePath: 'Keystore pad',
            keystorePathPlaceholder: '/etc/gzac/certs/client.p12',
            keystorePathTooltip:
                'Optioneel. Een JKS- of PKCS12-bestand met het clientcertificaat en de private key, op elke server van GZAC op hetzelfde pad.',
            keystoreSecret: 'Keystore wachtwoord',
            keystoreSecretTooltip:
                'Verplicht bij een keystore. Wordt na opslaan niet meer getoond; leeg laten behoudt het opgeslagen wachtwoord zolang het pad gelijk blijft.',
            truststorePath: 'Truststore pad',
            truststorePathPlaceholder: '/etc/gzac/certs/truststore.p12',
            truststorePathTooltip:
                'Optioneel. Een JKS- of PKCS12-bestand met de CA- of servercertificaten die vertrouwd worden in plaats van de standaardlijst van de JVM.',
            truststoreSecret: 'Truststore wachtwoord',
            tlsEnabled: 'Certificaten (mutual TLS)',
            tlsEnabledTooltip:
                'Schakel in om een clientcertificaat (mutual TLS) te gebruiken of een specifiek servercertificaat te vertrouwen, als PEM-tekst of als keystore-bestand. Bij token exchange gebruikt ook het token endpoint het clientcertificaat; bij een SAML token service alleen die service. Uitschakelen verwijdert de certificaten bij opslaan.',
            'tlsEnabled.toggleOn': 'Aan',
            'tlsEnabled.toggleOff': 'Uit',
            clientCertificate: 'Clientcertificaat (PEM)',
            clientCertificateTooltip:
                'Optioneel. Het certificaat waarmee GZAC zich bij mutual TLS identificeert. Leeg laten schakelt mutual TLS uit.',
            clientCertificatePlaceholder: '-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----',
            clientPrivateKey: 'Private key van het clientcertificaat (PEM)',
            clientPrivateKeyTooltip:
                'Verplicht bij een nieuw clientcertificaat. Mag niet versleuteld zijn. Wordt na opslaan niet meer getoond; leeg laten behoudt de opgeslagen sleutel.',
            serverCertificate: 'Servercertificaat (PEM, optioneel)',
            serverCertificateTooltip:
                'Optioneel. Het CA- of servercertificaat dat vertrouwd wordt in plaats van de standaardlijst van de JVM.',
            serverCertificatePlaceholder: '-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----'
        },
        en: {
            configurationTitle: 'Configuration name',
            configurationTitlePlaceholder: 'Client Authentication',
            configurationTitleTooltip:
                'Http Client Authentication',
            title: 'Http Client Authentication',
            description: 'Provides authentication and mutual TLS for outbound REST and SOAP clients, including the ZGW API, Haal Centraal and Suwinet plugins',
            authenticationType: 'Authentication type',
            authHeaderName: 'Authentication header name',
            authHeaderNamePlaceholder: 'X-Api-Key',
            authSecret: 'Authentication secret',
            clientId: 'Client ID',
            clientIdPlaceholder: 'valtimo_client',
            clientSecret: 'Client secret',
            clientSecretTooltip: 'At least 32 characters. Used to sign the ZGW JWT token.',
            tokenEndpoint: 'Token endpoint URL',
            tokenEndpointPlaceholder: 'https://keycloak.example.com/realms/example/protocol/openid-connect/token',
            tokenEndpointTooltip:
                'The Keycloak token endpoint. GZAC gets a token with client credentials here and exchanges it for a token for the audience.',
            audience: 'Audience',
            audiencePlaceholder: 'haal-centraal',
            audienceTooltip: 'The Keycloak client the token is exchanged for, usually the one of the API.',
            scope: 'Scope',
            scopePlaceholder: 'openid',
            scopeTooltip: 'Optional. Sent when getting the first token.',
            username: 'Username',
            usernamePlaceholder: 'valtimo',
            password: 'Password',
            samlTokenServiceUrl: 'SAML token service URL',
            samlTokenServiceUrlPlaceholder: 'https://sts.example.com/sts/requestsecuritytoken/v1?b64=true',
            samlTokenServiceUrlTooltip:
                'The full URL of the WS-Trust token service, with ?b64=true so the token comes back as one line. The certificates below are for this service only, not for the API.',
            samlHeaderName: 'Header for the token (optional)',
            samlHeaderNamePlaceholder: 'x-saml-attribute-token1',
            samlHeaderNameTooltip: 'Optional. Leave empty to send the token as Authorization: Bearer.',
            connectionTimeout: 'Connection timeout (ms, optional)',
            responseTimeout: 'Response timeout (ms, optional)',
            timeoutPlaceholder: '10000',
            timeoutTooltip: 'Optional, in milliseconds; default 10000. For the token service and the Haal Centraal reactor-netty client.',
            tlsSource: 'Certificates as',
            keystorePath: 'Keystore path',
            keystorePathPlaceholder: '/etc/gzac/certs/client.p12',
            keystorePathTooltip:
                'Optional. A JKS or PKCS12 file with the client certificate and private key, at the same path on every GZAC server.',
            keystoreSecret: 'Keystore password',
            keystoreSecretTooltip:
                'Required with a keystore. Not shown again after saving; leave empty to keep the stored password while the path stays the same.',
            truststorePath: 'Truststore path',
            truststorePathPlaceholder: '/etc/gzac/certs/truststore.p12',
            truststorePathTooltip:
                'Optional. A JKS or PKCS12 file with the CA or server certificates to trust instead of the JVM default list.',
            truststoreSecret: 'Truststore password',
            tlsEnabled: 'Certificates (mutual TLS)',
            tlsEnabledTooltip:
                'Turn on to present a client certificate (mutual TLS) or to trust a specific server certificate, as PEM text or as keystore file. With token exchange the token endpoint gets the client certificate too; with a SAML token service only that service does. Turning it off removes the certificates on save.',
            'tlsEnabled.toggleOn': 'On',
            'tlsEnabled.toggleOff': 'Off',
            clientCertificate: 'Client certificate (PEM)',
            clientCertificateTooltip:
                'Optional. The certificate GZAC presents for mutual TLS. Leave empty to turn mutual TLS off.',
            clientCertificatePlaceholder: '-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----',
            clientPrivateKey: 'Client certificate private key (PEM)',
            clientPrivateKeyTooltip:
                'Required with a new client certificate. Must not be encrypted. Not shown again after saving; leave empty to keep the stored key.',
            serverCertificate: 'Server certificate (PEM, optional)',
            serverCertificateTooltip:
                'Optional. The CA or server certificate to trust instead of the JVM default list.',
            serverCertificatePlaceholder: '-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----'
        },
        de: {
            configurationTitle: 'Konfigurationsname',
            configurationTitlePlaceholder: 'Client Authentication',
            configurationTitleTooltip:
                'Http Client Authentication',
            title: 'Http Client Authentication',
            description: 'Stellt Authentifizierung und Mutual TLS für ausgehende REST- und SOAP-Clients bereit, auch für die ZGW-API-, Haal-Centraal- und Suwinet-Plugins',
            authenticationType: 'Authentifizierungstyp',
            authHeaderName: 'Name des Authentifizierungs-Headers',
            authHeaderNamePlaceholder: 'X-Api-Key',
            authSecret: 'Authentifizierungs-Secret',
            clientId: 'Client-ID',
            clientIdPlaceholder: 'valtimo_client',
            clientSecret: 'Client-Secret',
            clientSecretTooltip: 'Mindestens 32 Zeichen. Wird zum Signieren des ZGW-JWT-Tokens verwendet.',
            tokenEndpoint: 'Token-Endpoint-URL',
            tokenEndpointPlaceholder: 'https://keycloak.example.com/realms/example/protocol/openid-connect/token',
            tokenEndpointTooltip:
                'Der Token-Endpoint von Keycloak. GZAC holt hier per Client Credentials ein Token und tauscht es gegen ein Token für die Audience.',
            audience: 'Audience',
            audiencePlaceholder: 'haal-centraal',
            audienceTooltip: 'Der Keycloak-Client, für den das Token getauscht wird, meist der der API.',
            scope: 'Scope',
            scopePlaceholder: 'openid',
            scopeTooltip: 'Optional. Wird beim Holen des ersten Tokens mitgeschickt.',
            username: 'Benutzername',
            usernamePlaceholder: 'valtimo',
            password: 'Passwort',
            samlTokenServiceUrl: 'URL des SAML-Token-Service',
            samlTokenServiceUrlPlaceholder: 'https://sts.example.com/sts/requestsecuritytoken/v1?b64=true',
            samlTokenServiceUrlTooltip:
                'Die vollständige URL des WS-Trust-Token-Service, mit ?b64=true, damit das Token als eine Zeile zurückkommt. Die Zertifikate unten gelten nur für diesen Service, nicht für die API.',
            samlHeaderName: 'Header für das Token (optional)',
            samlHeaderNamePlaceholder: 'x-saml-attribute-token1',
            samlHeaderNameTooltip: 'Optional. Leer lassen sendet das Token als Authorization: Bearer.',
            connectionTimeout: 'Verbindungs-Timeout (ms, optional)',
            responseTimeout: 'Antwort-Timeout (ms, optional)',
            timeoutPlaceholder: '10000',
            timeoutTooltip: 'Optional, in Millisekunden; Standard 10000. Für den Token-Service und den Reactor-Netty-Client von Haal Centraal.',
            tlsSource: 'Zertifikate als',
            keystorePath: 'Keystore-Pfad',
            keystorePathPlaceholder: '/etc/gzac/certs/client.p12',
            keystorePathTooltip:
                'Optional. Eine JKS- oder PKCS12-Datei mit Client-Zertifikat und Private Key, auf jedem GZAC-Server unter demselben Pfad.',
            keystoreSecret: 'Keystore-Passwort',
            keystoreSecretTooltip:
                'Pflicht bei einem Keystore. Wird nach dem Speichern nicht mehr angezeigt; leer lassen behält das gespeicherte Passwort, solange der Pfad gleich bleibt.',
            truststorePath: 'Truststore-Pfad',
            truststorePathPlaceholder: '/etc/gzac/certs/truststore.p12',
            truststorePathTooltip:
                'Optional. Eine JKS- oder PKCS12-Datei mit den CA- oder Serverzertifikaten, denen statt der Standardliste der JVM vertraut wird.',
            truststoreSecret: 'Truststore-Passwort',
            tlsEnabled: 'Zertifikate (Mutual TLS)',
            tlsEnabledTooltip:
                'Einschalten, um ein Client-Zertifikat (Mutual TLS) zu verwenden oder einem bestimmten Serverzertifikat zu vertrauen, als PEM-Text oder als Keystore-Datei. Bei Token Exchange nutzt auch der Token-Endpoint das Client-Zertifikat; bei einem SAML-Token-Service nur dieser Service. Ausschalten entfernt die Zertifikate beim Speichern.',
            'tlsEnabled.toggleOn': 'An',
            'tlsEnabled.toggleOff': 'Aus',
            clientCertificate: 'Client-Zertifikat (PEM)',
            clientCertificateTooltip:
                'Optional. Das Zertifikat, mit dem sich GZAC bei Mutual TLS ausweist. Leer lassen schaltet Mutual TLS aus.',
            clientCertificatePlaceholder: '-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----',
            clientPrivateKey: 'Private Key des Client-Zertifikats (PEM)',
            clientPrivateKeyTooltip:
                'Pflicht bei einem neuen Client-Zertifikat. Darf nicht verschlüsselt sein. Wird nach dem Speichern nicht mehr angezeigt; leer lassen behält den gespeicherten Schlüssel.',
            serverCertificate: 'Serverzertifikat (PEM, optional)',
            serverCertificateTooltip:
                'Optional. Das CA- oder Serverzertifikat, dem statt der Standardliste der JVM vertraut wird.',
            serverCertificatePlaceholder: '-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----'
        }
    }
};

export {httpClientAuthenticationPluginSpecification};

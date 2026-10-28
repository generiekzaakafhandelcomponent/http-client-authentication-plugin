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
            'authenticationTypeDescription.BEARER':
                'Stuurt het secret als Authorization: Bearer. Voor API\'s die een vast bearer token verwachten.',
            'authenticationTypeDescription.TOKEN':
                'Stuurt het secret als Authorization: Token. Voor de Objecten, Objecttypen en Klantinteracties API.',
            'authenticationTypeDescription.HEADER':
                'Stuurt het secret in een header die je zelf opgeeft, bijvoorbeeld X-Api-Key. Voor API\'s met een API key in een eigen header.',
            'authenticationTypeDescription.BEARER_AND_API_KEY':
                'Stuurt hetzelfde secret als Authorization: Bearer én als X-Api-Key header. Voor API\'s die beide verwachten, zoals Haal Centraal.',
            'authenticationTypeDescription.BASIC':
                'Stuurt een gebruikersnaam en wachtwoord als HTTP Basic authenticatie, bijvoorbeeld voor Suwinet.',
            'authenticationTypeDescription.ZGW_JWT':
                'Ondertekent bij elke call een JWT met de client ID en het secret, met daarin de gebruiker die de call doet. Voor Open Zaak, Open Notificaties en andere ZGW API\'s.',
            'authenticationTypeDescription.TOKEN_EXCHANGE':
                'Haalt met de client ID en het secret een token op bij Keycloak, wisselt dat in voor een token voor de audience en stuurt dat als bearer token.',
            'authenticationTypeDescription.SAML_TOKEN':
                'Vraagt bij elke call een SAML-token op bij een WS-Trust token service en stuurt dat als bearer token of in een header die je opgeeft.',
            'authenticationTypeDescription.NONE':
                'Stuurt geen credentials. Voor API\'s zonder authenticatie, of die alleen een clientcertificaat vragen (zie Certificaten hieronder).',
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
            'authenticationTypeDescription.BEARER':
                'Sends the secret as Authorization: Bearer. For APIs that expect a fixed bearer token.',
            'authenticationTypeDescription.TOKEN':
                'Sends the secret as Authorization: Token. For the Objecten, Objecttypen and Klantinteracties API.',
            'authenticationTypeDescription.HEADER':
                'Sends the secret in a header you name, for example X-Api-Key. For APIs with an API key in a header of their own.',
            'authenticationTypeDescription.BEARER_AND_API_KEY':
                'Sends the same secret as Authorization: Bearer and as X-Api-Key header. For APIs that expect both, such as Haal Centraal.',
            'authenticationTypeDescription.BASIC':
                'Sends a username and password as HTTP Basic authentication, for example for Suwinet.',
            'authenticationTypeDescription.ZGW_JWT':
                'Signs a JWT with the client ID and secret on every call, carrying the user who makes the call. For Open Zaak, Open Notificaties and other ZGW APIs.',
            'authenticationTypeDescription.TOKEN_EXCHANGE':
                'Gets a token from Keycloak with the client ID and secret, exchanges it for a token for the audience and sends that as bearer token.',
            'authenticationTypeDescription.SAML_TOKEN':
                'Requests a SAML token from a WS-Trust token service on every call and sends it as bearer token or in a header you name.',
            'authenticationTypeDescription.NONE':
                'Sends no credentials. For APIs without authentication, or that only ask for a client certificate (see Certificates below).',
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
        }
    }
};

export {httpClientAuthenticationPluginSpecification};

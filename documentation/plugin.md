# Plugin Documentation

## Overview

The Http Client Authentication plugin tells GZAC how to log in to an external API. A configuration holds the
credentials (a token, a client ID and secret, a token exchange with Keycloak, or a custom header) and, when the API
requires it, the certificates for a secure connection (mutual TLS).

You can select a configuration of this plugin as the authentication of the ZGW API plugins: Zaken API, Documenten API,
Catalogi API, Besluiten API, Notificaties API, Objecten API and Objecttypen API. Haal Centraal and Socrates (Plugins
repository) use it as well. Other plugins
that call an external API can use it as well.

## Installation

The plugin works in every Valtimo 12 application, with or without the ZGW modules. Add the dependencies below; there
is nothing to configure.

| To use it in                                       | Add                                                                              |
|----------------------------------------------------|----------------------------------------------------------------------------------|
| Plugins that ask for an `HttpClientAuthenticator`  | Only this plugin                                                                 |
| The ZGW API plugins                                | Only this plugin. It supports the ZGW API modules your application already has   |

The ZGW API modules are not brought along: the plugin adapts to the ones in your application. An application with only
the Objecten API, for example, can select the plugin in the Objecten API plugin (and in the Zaken, Documenten and
Catalogi API plugins, which the Objecten API module brings along). All authentication types, including ZGW JWT, and
mutual TLS work in every application.

### Backend

```kotlin
dependencies {
    implementation("com.ritense.valtimoplugins:http-client-authentication:2.2.1-V12")
}
```

### Frontend

```json
{
  "dependencies": {
    "@valtimo-plugins/http-client-authentication": "2.2.1-V12"
  }
}
```

Use the exact version: a range such as `^2.2.1-V12` also matches later Valtimo 13 releases (`2.x`), which need
Angular 19. Remove the Plugins repository's `http-client-authentication-plugin` (1.0.0) if the application has it: it
contains the same classes.

In your `app.module.ts`:

```typescript
import {
    HttpClientAuthenticationPluginModule, httpClientAuthenticationPluginSpecification,
} from '@valtimo-plugins/http-client-authentication';

@NgModule({
    imports: [
        HttpClientAuthenticationPluginModule,
    ],
    providers: [
        {
            provide: PLUGIN_TOKEN,
            useValue: [
                httpClientAuthenticationPluginSpecification,
            ]
        }
    ]
})
```

## Configuring the plugin

Go to **Admin > Plugins**, click **Configure plugin** and choose **Http Client Authentication**.

### Authentication type

Choose how GZAC logs in to the API:

| Authentication type             | Use it for                                    | Fields to fill in                                      |
|---------------------------------|-----------------------------------------------|--------------------------------------------------------|
| **Client ID / secret (ZGW JWT)** | Open Zaak and Open Notificaties               | **Client ID** and **Client secret** (at least 32 characters) |
| **Token**                       | Objecten and Objecttypen API                  | **Authentication secret**: the API token               |
| **Bearer**                      | APIs that expect a bearer token               | **Authentication secret**: the token                   |
| **Header**                      | APIs that expect a key in a header of their own | **Authentication header name** (for example `X-Api-Key`) and **Authentication secret** |
| **Token exchange**              | APIs behind Keycloak token exchange, such as Haal Centraal | **Token endpoint URL**, **Client ID**, **Client secret** and **Audience**; **Scope** is optional |
| **None**                        | APIs without authentication                   | –                                                      |

![Token exchange configuration](assets/plugin/01-token-exchange.png)

With **Token exchange**, GZAC gets a token from the Keycloak token endpoint with its client ID and secret, exchanges it
for a token for the audience (usually the Keycloak client of the API) and sends that as bearer token. The token is
reused until shortly before it expires, also by other plugins that use the same configuration.

GZAC sets the credentials on every call, so a token is never sent after it expired, and a ZGW JWT carries the user who
made the call.

Secrets are not shown again after saving. When you edit a configuration, leave the secret empty to keep the stored
one. If you switch to another authentication type, enter the secret again.

### Certificates (mutual TLS)

Some APIs are behind a gateway that only accepts calls from a known client certificate, or use a certificate that is
not publicly trusted. Turn on **Certificates (mutual TLS)** for any authentication type, and choose under
**Certificates as** where they come from: **PEM** (pasted into the screen) or **Keystore (JKS / PKCS12)** (files on the
server). A configuration uses one of the two.

As **PEM**:

- **Client certificate (PEM)** – the certificate GZAC presents to the API. Paste the contents of the `.pem` or `.crt`
  file, starting with `-----BEGIN CERTIFICATE-----`. Leave it empty if the API does not ask for a client certificate.
  Don't paste the private key into this field: it is not stored encrypted, so GZAC refuses it.
- **Client certificate private key (PEM)** – the private key that belongs to the client certificate, starting with
  `-----BEGIN PRIVATE KEY-----` (or `RSA PRIVATE KEY` / `EC PRIVATE KEY`). Keys protected with a password are not
  supported. The key is not shown again after saving; when editing, leave it empty to keep the stored key.
- **Server certificate (PEM)** – optional. The certificate (or CA certificate) of the API to trust. Fill it in when the
  API uses a certificate that is not publicly trusted, for example one issued by an internal CA. When it is filled in,
  only that certificate is trusted for this configuration.

As **Keystore (JKS / PKCS12)**:

- **Keystore path** and **Keystore password** – a JKS or PKCS12 file with the client certificate and its private key.
- **Truststore path** and **Truststore password** – optional. A JKS or PKCS12 file with the certificates to trust
  instead of the publicly trusted ones.

![Certificates from a keystore file](assets/plugin/02-certificates-keystore.png)

The files must be at the same path on every server that runs GZAC. GZAC reads them on the first call, not when saving:
a wrong path or password shows up as an error on that call, naming the field. A replaced file is picked up on the next
call. The passwords are not shown again after saving; when editing, leave them empty to keep the stored ones as long
as the path stays the same.

With **Token exchange**, the calls to the Keycloak token endpoint present the client certificate too. For those calls
GZAC trusts the server certificate you configured as well as the publicly trusted ones, so a Keycloak with a public
certificate is still reached.

To stop using certificates, turn the toggle off and save. The certificates are removed. Stored private keys and
passwords stay saved but are no longer used.

**Save configuration** stays disabled until the configuration is complete: required fields filled in (fields with
only spaces count as empty), a token endpoint that is an `http(s)://` URL, a valid header name, certificates and a
private key in PEM format (with their line breaks), and a password for every keystore path. Fields marked
*(required)* are the ones still needed. Spaces around values that aren't secret are removed when saving.

When saving, GZAC also checks what the screen can't, such as whether the private key belongs to the client
certificate. If it refuses, the screen shows a general error ("An unexpected error occurred"); the application log
names the field.

### Using it in a ZGW API plugin

1. Create a configuration of this plugin as described above, with the authentication type the API expects.
2. Open the configuration of the ZGW API plugin (for example **Zaken API** or **Objecten API**) and select your
   configuration in its authentication field.

## Configuration files

Configurations can also be deployed with a `*.pluginconfig.json` file. These are checked in the same way as in the
screen, on every start. Valtimo 12 logs an invalid configuration and skips it and the rest of its file. Keystore files
are not read at that moment. The property names are:

| Property             | Field in the screen                    | Values                                              |
|----------------------|----------------------------------------|-----------------------------------------------------|
| `authenticationType` | Authentication type                    | `ZGW_JWT`, `TOKEN`, `BEARER`, `HEADER`, `TOKEN_EXCHANGE` or `NONE` |
| `clientId`           | Client ID                              |                                                     |
| `authSecret`         | Client secret (ZGW JWT) / Authentication secret |                                            |
| `authHeaderName`     | Authentication header name             |                                                     |
| `tokenEndpoint`      | Token endpoint URL                     | URL                                                 |
| `clientSecret`       | Client secret (token exchange)         |                                                     |
| `audience`           | Audience                               |                                                     |
| `scope`              | Scope                                  | Optional                                            |
| `clientCertificate`  | Client certificate (PEM)               | PEM text, with line breaks written as `\n`          |
| `clientPrivateKey`   | Client certificate private key (PEM)   | PEM text, with line breaks written as `\n`          |
| `serverCertificate`  | Server certificate (PEM)               | PEM text, with line breaks written as `\n`          |
| `keystorePath`       | Keystore path                          | Path of a JKS or PKCS12 file                        |
| `keystoreSecret`     | Keystore password                      |                                                     |
| `truststorePath`     | Truststore path                        | Path of a JKS or PKCS12 file                        |
| `truststoreSecret`   | Truststore password                    |                                                     |

See the [Example Application](example-application.md) for complete examples.

## Using it from another plugin

A plugin that calls an external API can let users pick a configuration of this plugin. Add a `@PluginProperty` of type
`HttpClientAuthenticator`, and call `applyAuth` on the `RestClient.Builder` before making the call:

```kotlin
class SomeOtherPlugin(
    private val httpClientAuthenticator: HttpClientAuthenticator?
) {
    fun callApi(): RestClient {
        var builder = RestClient.builder()
        httpClientAuthenticator?.let { builder = it.applyAuth(builder) }
        return builder.build()
    }
}
```

Look the plugin up by the `HttpClientAuthenticator` interface or by its key `http-client-authentication`, not
by a plugin class: the class differs per application (see below).

`applyAuth` sets the credentials on every request of the built client, and the certificates through the client's
request factory. A configuration is also an `ExchangeFilterFunction` for a `WebClient`; that only sets the credentials,
because a `WebClient` keeps its own connection settings.

The plugin has no process actions.

## How the ZGW support works

The ZGW API plugins only accept a plugin that implements their own authentication interface, such as
`ZakenApiAuthentication`, and those interfaces live in the ZGW modules. So the plugin contains a plugin class for every
combination of ZGW modules (generated at build time), all with the same key and the same properties. At startup the
plugin checks which ZGW modules are on the classpath and lets Valtimo scan only the matching class, by adding its
package to `valtimo.annotation-scan.accepted-packages`. The packages your application configured there are kept.

When you add or remove a ZGW module, the next startup picks another class under the same key; existing
configurations keep working.

The classes live in packages under `valtimoplugins.httpclientauthentication.variant`. If Valtimo scans more than the
selected one, for example because `valtimo.annotation-scan.accepted-packages` includes
`valtimoplugins.httpclientauthentication`, the application stops at startup with a message that says which class it
expected.

## Upgrading from the old plugin key

`2.1.0-V12` and the Plugins repository's `http-client-authentication-plugin` (1.0.0) used the key
`http-client-authentication-plugin`; `2.1.1-V12` and later use `http-client-authentication`. `2.2.0-V12` moves
existing configurations of the old key to the new one when the application starts, with their secrets, and the
plugins that use them keep working.

- Update the backend and frontend to matching versions. The frontend `pluginId` must equal the backend key.
- In `*.pluginconfig.json` files, change `pluginDefinitionKey` to `http-client-authentication` and keep the ids. A file
  that still uses the old key is skipped after the move.
- Stop all nodes before starting `2.2.0-V12`: nodes with another plugin version can't share the database.
- With `2.1.1-V12` the configurations of the old key had to be recreated by hand; that is no longer needed.

## Migrating from token-exchange-authentication

The standalone `token-exchange-authentication` plugin is superseded by the `TOKEN_EXCHANGE` type of this plugin.
Its property keys are the same, so a configuration migrates by changing the plugin definition key and adding the
authentication type:

```json
{
    "pluginDefinitionKey": "http-client-authentication",
    "properties": {
        "authenticationType": "TOKEN_EXCHANGE",
        "tokenEndpoint": "...",
        "clientId": "...",
        "clientSecret": "...",
        "audience": "...",
        "keystorePath": "...",
        "keystoreSecret": "..."
    }
}
```

Consumers reference the `http-client-authentication` category (`HttpClientAuthenticator`). The
`TokenExchangeAuthentication` interface and the `token-exchange-authentication` category are not carried over.
Remove the `token-exchange-authentication` dependency (backend and frontend) after migrating.

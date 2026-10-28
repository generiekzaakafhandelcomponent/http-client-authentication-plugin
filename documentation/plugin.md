# Plugin Documentation

## Overview

The Http Client Authentication plugin tells GZAC how to log in to an external API, over REST or SOAP. A configuration
holds the credentials (a token, a client ID and secret, a username and password, a token exchange with Keycloak, a
token from a SAML token service, or a custom header) and, when the API requires it, the certificates for a secure
connection (mutual TLS).

You can select a configuration of this plugin wherever one of these auth plugins can be selected, so one screen covers
all of them:

| Instead of                                                         | Selected by, for example                         | Choose the type                                           |
|--------------------------------------------------------------------|--------------------------------------------------|-----------------------------------------------------------|
| The ZGW API authentication (`openzaak`, `objecttokenauthentication`, …) | Zaken, Documenten, Catalogi, Besluiten, Notificaties, Objecten, Objecttypen and Klantinteracties API | **Client ID / secret (ZGW JWT)** or **Token**             |
| `alfrescoauthentication`                                           | Documenten API                                   | **Client ID / secret (ZGW JWT)**                          |
| `haal-centraal-auth`                                               | Haal Centraal BRP / BAG (`haal-centraal` 2.x)    | **Bearer and X-Api-Key**, or **Header** for an API key only |
| `haal-centraal-authentication-plugin` (SAML)                       | Haal Centraal BRP / BAG on Valtimo 12 (`haal-centraal` 0.13); on Valtimo 13 select a **SAML token service** configuration through `haal-centraal-auth` | **SAML token service**                                    |
| `suwinet-auth` (SOAP)                                              | Suwinet                                          | `MTLS`: any type except **SAML token service**, with keystore certificates; `BASIC`: **Basic**; `HEADER`: **Header**; `EXTERN`: **None** |
| `tokenauthentication`                                              | Plugins that select its category                 | **Token**                                                 |
| `token-exchange-authentication`                                    | Plugins that select its category                 | **Token exchange**                                        |
| `mTLS-SSLContext`                                                  | Rotterdam Oracle EBS                             | Any type except **SAML token service**, with certificates |

This is an alternative, not a replacement: the other auth plugins keep working next to this one, and you choose which
to configure. Other plugins that call an external API can use this plugin as well (see
[Using it from another plugin](#using-it-from-another-plugin)).

## Installation

The plugin works in every Valtimo 13 application, with any combination of the plugins above, or none. Add the
dependencies below; there is nothing to configure.

| To use it in                                       | Add                                                                              |
|----------------------------------------------------|----------------------------------------------------------------------------------|
| Plugins that ask for an `HttpClientAuthenticator`  | Only this plugin                                                                 |
| The ZGW API plugins                                | Only this plugin. It supports the ZGW API modules your application already has   |
| A plugin that selects one of the auth plugins above | This plugin, next to the auth plugin that plugin needs (for example `suwinet-auth` for Suwinet, `haal-centraal-authentication` for Haal Centraal 2.x) |

None of these modules is brought along: the plugin adapts to the ones in your application. It offers a category only
when the interface of that category is on the classpath, which the plugin that selects it needs anyway. An application
with only the Objecten API, for example, can select the plugin in the Objecten API plugin (and in the Zaken, Documenten
and Catalogi API plugins, which the Objecten API module brings along); one with only Suwinet can select it in Suwinet.
All authentication types and mutual TLS work in every application. SOAP needs Apache CXF, which `suwinet-auth` brings.

### Backend

```kotlin
dependencies {
    implementation("com.ritense.valtimoplugins:http-client-authentication:3.1.0")
}
```

### Frontend

```json
{
  "dependencies": {
    "@valtimo-plugins/http-client-authentication": "3.1.0"
  }
}
```

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
| **Bearer**                      | APIs that expect a bearer token               | **Authentication secret**: the token                   |
| **Token**                       | Objecten, Objecttypen and Klantinteracties API | **Authentication secret**: the API token               |
| **Header**                      | APIs that expect a key in a header of their own | **Authentication header name** (for example `X-Api-Key`) and **Authentication secret** |
| **Bearer and X-Api-Key**        | APIs that expect the secret as bearer token and as `X-Api-Key` header, such as Haal Centraal (as `haal-centraal-auth` does) | **Authentication secret** |
| **Basic (username / password)** | APIs with HTTP Basic authentication, such as Suwinet | **Username** (without `:`) and **Password**         |
| **Client ID / secret (ZGW JWT)** | Open Zaak and Open Notificaties               | **Client ID** and **Client secret** (at least 32 characters) |
| **Token exchange**              | APIs behind Keycloak token exchange, such as Haal Centraal | **Token endpoint URL**, **Client ID**, **Client secret** and **Audience**; **Scope** is optional |
| **SAML token service**          | Haal Centraal behind a WS-Trust token service | **SAML token service URL**; **Header for the token**, **Connection timeout** and **Response timeout** are optional |
| **None**                        | APIs without authentication                   | –                                                      |

With **Token exchange**, GZAC gets a token from the Keycloak token endpoint with its client ID and secret, exchanges it
for a token for the audience (usually the Keycloak client of the API) and sends that as bearer token. The token is
reused until shortly before it expires, also by other plugins that use the same configuration.

With **SAML token service**, GZAC asks the token service for a SAML 2.0 token (a WS-Trust `RequestSecurityToken`) on
every call and sends the response as bearer token, or in the header you name (for example `x-saml-attribute-token1`).
Fill in the full URL, including the path and `?b64=true` (for example
`https://sts.example.com/sts/requestsecuritytoken/v1?b64=true`): unlike `haal-centraal-authentication-plugin`, GZAC
doesn't add them, and without `?b64=true` the service answers with XML, which can't be sent as a header (the call fails
with "did not return a single-line token"). The URL must be `https`. The certificates of this configuration are used
for the token service only, not for the API, and when a server certificate or truststore is configured, only that is
trusted for the token service. The timeouts are in milliseconds (default 10000).

GZAC sets the credentials on every call, so a token is never sent after it expired, and a ZGW JWT carries the user who
made the call. Secrets sent in a header (Bearer, Header, Token, Bearer and X-Api-Key) are sent without surrounding whitespace, such as
the line break at the end of a secret file, and must be text that can be sent in a header: no line breaks or other
control characters within, and no characters beyond Latin-1 (such as `€`). **Basic** sends the username and password
as typed (UTF-8), like `suwinet-auth`: a line break at the end of a password file is part of the password.

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
  Don't paste the private key into this field: it is not stored encrypted. If you do, **Save configuration** stays
  disabled (the private key field below shows *(required)*).
- **Client certificate private key (PEM)** – the private key that belongs to the client certificate, starting with
  `-----BEGIN PRIVATE KEY-----` (or `RSA PRIVATE KEY` / `EC PRIVATE KEY`). Keys protected with a password are not
  supported. The key is not shown again after saving; when editing, leave it empty to keep the stored key, as long as
  the client certificate stays the same. A new client certificate needs its key again.
- **Server certificate (PEM, optional)** – optional. The certificate (or CA certificate) of the API to trust. Fill it in when the
  API uses a certificate that is not publicly trusted, for example one issued by an internal CA. When it is filled in,
  only that certificate is trusted for this configuration.

As **Keystore (JKS / PKCS12)**:

- **Keystore path** and **Keystore password** – a JKS or PKCS12 file with the client certificate and its private key.
- **Truststore path** and **Truststore password** – optional. A JKS or PKCS12 file with the certificates to trust
  instead of the publicly trusted ones.

The files must be at the same path on every server that runs GZAC. GZAC reads them on the first call, not when saving:
a wrong path or password shows up as an error on that call, naming the field. A replaced file is picked up on the next
call. The passwords are not shown again after saving; when editing, leave them empty to keep the stored ones as long
as the path stays the same.

With **Token exchange**, the calls to the Keycloak token endpoint present the client certificate too. For the Keycloak
token endpoint GZAC trusts the server certificate you configured as well as the publicly trusted ones, so a Keycloak
with a public certificate is still reached. With **SAML token service**, only the token service gets the certificates,
and only the configured server certificate is trusted there (see above).

To stop using certificates, turn the toggle off and save. The certificates are removed. Stored private keys and
passwords stay saved but are no longer used: to use certificates again, fill in the certificate and its key (or the
keystore path and its password) again.

**Save configuration** stays disabled until the configuration is complete: required fields filled in (fields with
only spaces count as empty), a token endpoint that is an `http(s)://` URL, a valid header name, certificates and a
private key in PEM format (with their line breaks), and a password for every keystore path. Fields marked
*(required)* must be filled in; a value in the wrong format is not marked, it only keeps **Save configuration**
disabled. Spaces around values that aren't secret are removed when saving.

When saving, GZAC also checks what the screen can't, such as whether the private key belongs to the client
certificate. If it refuses, the screen shows a general error ("An unexpected error occurred") with a reference ID. Look that ID up
in the application log: the stack trace of that entry names the property (see
[Configuration files](#configuration-files) for the field of each property).

### Using it in a ZGW API plugin

1. Create a configuration of this plugin as described above, with the authentication type the API expects.
2. Open the configuration of the ZGW API plugin (for example **Zaken API** or **Objecten API**) and select your
   configuration in its authentication field.

## Configuration files

Configurations can also be deployed with a `*.pluginconfig.json` file. These are checked in the same way as in the
screen, on every start; an invalid configuration stops the application from starting. Keystore files are not read at
that moment. The property names are:

| Property             | Field in the screen                    | Values                                              |
|----------------------|----------------------------------------|-----------------------------------------------------|
| `authenticationType` | Authentication type                    | `ZGW_JWT`, `TOKEN`, `BEARER`, `HEADER`, `TOKEN_EXCHANGE`, `BASIC`, `BEARER_AND_API_KEY`, `SAML_TOKEN` or `NONE` |
| `clientId`           | Client ID                              |                                                     |
| `username`           | Username (Basic)                       |                                                     |
| `authSecret`         | Client secret (ZGW JWT) / Authentication secret / Password |                                 |
| `authHeaderName`     | Authentication header name / Header for the token (SAML) |                                   |
| `tokenEndpoint`      | Token endpoint URL / SAML token service URL | URL                                            |
| `connectionTimeout`  | Connection timeout (SAML)              | Milliseconds, optional (default 10000)              |
| `responseTimeout`    | Response timeout (SAML)                | Milliseconds, optional (default 10000)              |
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

Select it with a `@PluginProperty` typed as `HttpClientAuthenticator` (or one of the covered interfaces), or look
configurations up by the key `http-client-authentication`, not by a plugin class: the class is generated when the
application starts (see below).

### What each covered interface gets

| Interface                                       | Methods                                   | What a configuration provides                                                 |
|-------------------------------------------------|-------------------------------------------|-------------------------------------------------------------------------------|
| ZGW API authentication, `TokenAuthentication`, `HttpClientAuthenticator` | `applyAuth(RestClient.Builder)`, `filter` | Credentials on every request, certificates through the request factory |
| `HaalCentraalAuthentication` (both)             | `applyAuth`, `filter`, `getAuthenticatedHttpClient()` | The same; the reactor-netty client gets the certificates, the headers on every request and the timeouts |
| `SuwinetAuth`                                   | `applyAuth(Client)` (Apache CXF, SOAP)    | Certificates on the HTTP conduit (a copy of its TLS parameters with the key and trust managers), headers on every message; the consumer's timeouts and headers stay |
| `TokenExchangeAuthentication`                   | `getAccessToken()`, `getSslContext()`     | The bearer token of a type that has one (Bearer, Bearer and X-Api-Key, ZGW JWT, Token exchange, SAML token service; other types fail), and the certificates as `SSLContext` (or none) |
| `MTlsSslContext`                                | `createSslContext()`                      | Only the certificates; the caller sends no credentials of this configuration, and it fails without certificates |

With **SAML token service**, the certificates are never used for the API: `getSslContext()` returns none and
`createSslContext()` fails.

Known limits:

- Plugins that look up a concrete plugin class instead of the category interface can't use this plugin, even though
  they list it: `open-product` (its property is typed `TokenAuthenticationPlugin`) and the template list of `xential`
  (it looks up `MTlsSslContextPlugin`).
- Suwinet's verbose logging masks only the `Authorization` and `x-opentunnel-api-key` headers. A secret in another
  header (`X-Api-Key`, or a custom **Header**) is logged when that logging is on, as with `suwinet-auth`.
- `suwinet-auth` allowed a truststore without password; here a truststore needs its password.

`applyAuth` sets the credentials on every request of the built client, and the certificates through the client's
request factory. A configuration is also an `ExchangeFilterFunction` for a `WebClient`; that only sets the credentials,
because a `WebClient` keeps its own connection settings.

The plugin has no process actions.

## How the plugin adapts to your application

A plugin that selects an auth plugin only accepts a plugin that implements that auth plugin's interface, such as
`ZakenApiAuthentication` or `SuwinetAuth`, and those interfaces live in other modules. So when the application starts,
the plugin generates its plugin class,
`com.ritense.valtimoplugins.httpclientauthentication.plugin.GeneratedHttpClientAuthenticationPlugin`, implementing
exactly the covered interfaces on the classpath, and deploys its definition itself (Valtimo only finds plugin classes
that exist as class files). An interface whose implementation needs a library that is missing (CXF for `SuwinetAuth`)
is left out, with a warning in the log.

When you add or remove a module, the next startup deploys the plugin with the categories of the new set; existing
configurations keep working.

If another library declares the plugin key `http-client-authentication` too (for example an older copy of this
plugin), the application stops at startup with a message that names that class.

## Upgrading to 3.1.0

Nothing to change: configurations, properties and `*.pluginconfig.json` files stay as they are, unless a secret sent in
a header has a control character or a character beyond Latin-1 within it (see [Authentication type](#authentication-type)):
such a configuration is refused, and in a `*.pluginconfig.json` that stops the application from starting. The plugin class gets
a new name (see above), so stop all nodes before starting `3.1.0`: nodes with another plugin version can't share the
database. Code that looked the plugin up by class needs the interface or the key (as before).

## Upgrading to 3.0.0

`3.0.0` changes the plugin key from `http-client-authentication-plugin` to `http-client-authentication`, the key the
Valtimo 12 versions use since `2.1.1-V12`, and the title to "Http Client Authentication". Existing configurations move
to the new key when the application starts, with their secrets, and the plugins that use them keep working. A Valtimo
12 application that upgrades to Valtimo 13 keeps its configurations too.

- Update the backend and frontend together. The frontend `pluginId` must equal the backend key.
- In `*.pluginconfig.json` files, change `pluginDefinitionKey` to `http-client-authentication` and keep the ids. A file
  that still uses the old key stops the application from starting.
- Code that looks the plugin up by its key needs the new key.
- Stop all nodes before starting `3.0.0`: nodes with another plugin version can't share the database.


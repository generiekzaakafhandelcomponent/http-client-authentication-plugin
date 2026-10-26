# Plugin Documentation

## Overview

The Http Client Authentication plugin tells GZAC how to log in to an external API. A configuration holds the
credentials (a token, a client ID and secret, or a custom header) and, when the API requires it, the certificates for a
secure connection (mutual TLS).

You can select a configuration of this plugin as the authentication of the ZGW API plugins: Zaken API, Documenten API,
Catalogi API, Besluiten API, Notificaties API, Objecten API, Objecttypen API and Klantinteracties API. Other plugins
that call an external API can use it as well.

## Installation

The plugin works in every Valtimo 13 application, with or without the ZGW modules. Add the dependencies below; there
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
    implementation("com.ritense.valtimoplugins:http-client-authentication:2.1.0")
}
```

### Frontend

```json
{
  "dependencies": {
    "@valtimo-plugins/http-client-authentication": "2.1.0"
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

Go to **Admin > Plugins**, click **Configure plugin** and choose **Http Client Authentication Plugin**.

### Authentication type

Choose how GZAC logs in to the API:

| Authentication type             | Use it for                                    | Fields to fill in                                      |
|---------------------------------|-----------------------------------------------|--------------------------------------------------------|
| **Client ID / secret (ZGW JWT)** | Open Zaak and Open Notificaties               | **Client ID** and **Client secret** (at least 32 characters) |
| **Token**                       | Objecten, Objecttypen and Klantinteracties API | **Authentication secret**: the API token               |
| **Bearer**                      | APIs that expect a bearer token               | **Authentication secret**: the token                   |
| **Header**                      | APIs that expect a key in a header of their own | **Authentication header name** (for example `X-Api-Key`) and **Authentication secret** |
| **None**                        | APIs without authentication                   | –                                                      |

Secrets are not shown again after saving. When you edit a configuration, leave the secret empty to keep the stored
one. If you switch to another authentication type, enter the secret again.

### Certificates (mutual TLS)

Some APIs are behind a gateway that only accepts calls from a known client certificate, or use a certificate that is
not publicly trusted. Turn on **Certificates (mutual TLS)** to fill in:

- **Client certificate (PEM)** – the certificate GZAC presents to the API. Paste the contents of the `.pem` or `.crt`
  file, starting with `-----BEGIN CERTIFICATE-----`. Leave it empty if the API does not ask for a client certificate.
- **Client certificate private key (PEM)** – the private key that belongs to the client certificate, starting with
  `-----BEGIN PRIVATE KEY-----` (or `RSA PRIVATE KEY` / `EC PRIVATE KEY`). Keys protected with a password are not
  supported. The key is not shown again after saving; when editing, leave it empty to keep the stored key.
- **Server certificate (PEM)** – optional. The certificate (or CA certificate) of the API to trust. Fill it in when the
  API uses a certificate that is not publicly trusted, for example one issued by an internal CA. When it is filled in,
  only that certificate is trusted for this configuration.

To stop using certificates, turn the toggle off and save. The certificates are removed. A stored private key stays
saved but is no longer used.

When saving, GZAC checks the configuration and shows which field is wrong, for example when the private key does not
belong to the client certificate, when a field does not contain a valid certificate, or when a required field is empty.

### Using it in a ZGW API plugin

1. Create a configuration of this plugin as described above, with the authentication type the API expects.
2. Open the configuration of the ZGW API plugin (for example **Zaken API** or **Objecten API**) and select your
   configuration in its authentication field.

## Configuration files

Configurations can also be deployed with a `*.pluginconfig.json` file. These are checked in the same way as in the
screen; an invalid configuration stops the application from starting. The property names are:

| Property             | Field in the screen                    | Values                                              |
|----------------------|----------------------------------------|-----------------------------------------------------|
| `authenticationType` | Authentication type                    | `ZGW_JWT`, `TOKEN`, `BEARER`, `HEADER` or `NONE`    |
| `clientId`           | Client ID                              |                                                     |
| `authSecret`         | Client secret / Authentication secret  |                                                     |
| `authHeaderName`     | Authentication header name             |                                                     |
| `clientCertificate`  | Client certificate (PEM)               | PEM text, with line breaks written as `\n`          |
| `clientPrivateKey`   | Client certificate private key (PEM)   | PEM text, with line breaks written as `\n`          |
| `serverCertificate`  | Server certificate (PEM)               | PEM text, with line breaks written as `\n`          |

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

Look the plugin up by the `HttpClientAuthenticator` interface or by its key `http-client-authentication-plugin`, not
by a plugin class: the class differs per application (see below).

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


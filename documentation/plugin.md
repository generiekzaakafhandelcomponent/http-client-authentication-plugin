# Plugin Documentation

## Overview

The Http Client Authentication plugin authenticates outbound REST clients. A configured instance applies an
authentication header (bearer token, custom header, `Authorization: Token` or a ZGW JWT) to a Spring
`RestClient.Builder`, and can additionally present a client certificate (mutual TLS) and trust a specific server
certificate.

It can be used in two ways:

- as the authentication of the GZAC ZGW API plugins: Zaken API, Documenten API, Catalogi API, Besluiten API,
  Notificaties API, Objecten API, Objecttypen API and Klantinteracties API. The plugin implements each of their
  authentication categories, so a configuration of it can be selected in their "authentication" field, in place of the
  OpenZaak, Notificaties API Authentication, Object Token Authentication or OpenKlant Token Authentication plugins;
- by other plugins that depend on the `http-client-authentication` `@PluginCategory`.

## Requirements

From version 3.0.0 the plugin needs the GZAC ZGW modules on the host application's classpath, as provided by
`com.ritense.valtimo:valtimo-gzac-dependencies`: `zaken-api`, `documenten-api`, `catalogi-api`, `besluiten-api`,
`notificaties-api`, `objecten-api`, `objecttypen-api`, `klantinteracties-api` and `openzaak-plugin-authentication`.
A Valtimo application without them fails to start with this version; use version 2.x there.

## Dependencies

### Backend

```kotlin
dependencies {
    implementation("com.ritense.valtimoplugins:http-client-authentication:3.0.0")
}
```

### Frontend

```json
{
  "dependencies": {
    "@valtimo-plugins/http-client-authentication": "3.0.0"
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

## Configuration

| Property           | Type            | Required                              | Description                                                                                         |
|--------------------|-----------------|---------------------------------------|-----------------------------------------------------------------------------------------------------|
| authenticationType | enum            | Yes                                   | One of `NONE`, `BEARER`, `HEADER`, `TOKEN`, `ZGW_JWT`. Determines which authentication header is set |
| authHeaderName     | string          | Only for `HEADER`                     | Name of the header that carries `authSecret`                                                        |
| authSecret         | string (secret) | For `BEARER`, `HEADER`, `TOKEN`, `ZGW_JWT` | The bearer token, the header value, the token, or the client secret                             |
| clientId           | string          | Only for `ZGW_JWT`                    | The client ID put in the ZGW JWT                                                                    |
| clientCertificate  | string (PEM)    | No                                    | Client certificate (chain) presented for mutual TLS. Leave empty to turn mutual TLS off             |
| clientPrivateKey   | string (secret, PEM) | When `clientCertificate` is set  | Unencrypted private key of the client certificate (PKCS#8, PKCS#1 or EC)                            |
| serverCertificate  | string (PEM)    | No                                    | CA or server certificate to trust instead of the JVM's default trust store                          |

### Authentication types

- `NONE` — no authentication header is added.
- `BEARER` — sets `Authorization: Bearer <authSecret>`.
- `HEADER` — sets a custom header named `authHeaderName` with value `authSecret`.
- `TOKEN` — sets `Authorization: Token <authSecret>`, as used by the Objecten, Objecttypen and Klantinteracties APIs.
- `ZGW_JWT` ("Client ID / secret") — sets `Authorization: Bearer <jwt>`, where the JWT is signed with `authSecret`
  for `clientId`, exactly as the OpenZaak plugin does for Open Zaak. The secret must be at least 32 characters.

### Mutual TLS and the server certificate

The certificate fields take PEM text as-is, for example the contents of the files passed to curl's `--cert`, `--key`
and `--cacert`:

- With a `clientCertificate` and `clientPrivateKey`, every call presents that certificate to the server.
- With a `serverCertificate`, the server is trusted when its certificate is, or chains to, that certificate; the JVM's
  default trust store is then not used for this configuration. Without it, the JVM's default trust store applies.
- In the configuration screen the certificate fields are behind the "Certificates (mutual TLS)" toggle. It is on when
  a client or server certificate is stored. Saving with the toggle off removes both certificates.
- The client certificate is the switch for mutual TLS. The private key is a secret and is not shown again after
  saving; leaving it empty when editing keeps the stored key. To turn mutual TLS off, empty the client certificate.
- Saving is refused when the client certificate has no private key, when the private key does not belong to the
  client certificate, or when a field does not contain valid PEM. The error names the field.
  This also applies to autodeployed configurations (`*.pluginconfig.json`): an invalid one stops the application
  from starting.
  In a `*.pluginconfig.json` file, PEM text is a JSON string with its line breaks written as `\n`.
- Password-protected private keys are not supported.
- Calls use Valtimo's `valtimo.http.rest-client` connect, connection-request and read timeouts, as other outbound calls
  do.
- Mutual TLS applies to `RestClient`-based calls (`applyAuth`). The deprecated WebClient filter of the ZGW
  authentication interfaces only adds the authentication header.

## Actions

This plugin does not expose any process actions (`@PluginAction`). It only implements the `HttpClientAuthenticator`
interface (`@PluginCategory("http-client-authentication")`), so it is meant to be referenced by other plugins that
need to authenticate outbound HTTP calls, for example:

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

## Usage

### As authentication of a ZGW API plugin

1. Create a configuration of the Http Client Authentication plugin with the authentication type the API expects
   (`ZGW_JWT` for Open Zaak and Open Notificaties, `TOKEN` for the Objecten, Objecttypen and Klantinteracties APIs),
   and the certificate fields when the API is behind a gateway that requires mutual TLS.
2. In the Zaken API, Objecten API or other ZGW API plugin configuration, select it as the authentication plugin.

### From another plugin

1. Create a configuration of the Http Client Authentication plugin, choosing an authentication type (see above).
2. In a plugin that performs outbound REST calls, add a `@PluginProperty` of type `HttpClientAuthenticator` so a
   user can link a configured instance of this plugin to it.
3. Call `applyAuth(builder)` on the injected `HttpClientAuthenticator` before executing the outbound request.

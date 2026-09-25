# Plugin Documentation

## Overview

The Http Client Authentication plugin authenticates outbound REST clients. Other plugins depend on the
`http-client-authentication` `@PluginCategory` and use a configured instance to apply authentication to a Spring
`RestClient.Builder` before making outbound calls.

Four authentication types are supported:

| Type             | What is sent                                                                                   |
|------------------|------------------------------------------------------------------------------------------------|
| `NONE`           | Nothing                                                                                        |
| `BEARER`         | `Authorization: Bearer <authSecret>`                                                           |
| `HEADER`         | `<authHeaderName>: <authSecret>`                                                               |
| `TOKEN_EXCHANGE` | `Authorization: Bearer <jwt>`, where the JWT is obtained via a Keycloak token-exchange flow    |

Credentials are added by a request interceptor, so they are resolved on every request. For `TOKEN_EXCHANGE`
this means a long-lived `RestClient` never sends an expired token.

### Token exchange

`TOKEN_EXCHANGE` performs a two-step exchange against a Keycloak (or other OIDC) token endpoint:

1. `grant_type=client_credentials` to obtain a subject access token for the configured client.
2. `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` with that subject token to obtain a JWT scoped to
   the configured `audience`.

The JWT is cached in memory per configuration until shortly before it expires.

### mTLS (optional)

Some gateways (for example the ZGW wsgateway) require a client certificate. mTLS is only available for the
`TOKEN_EXCHANGE` type. When `keystorePath` and `keystoreSecret` are configured, the plugin builds an `SSLContext`
from that JKS keystore (and the optional truststore) and uses it for the outbound client.

A configuration of any other type that sets `keystorePath` or `truststorePath` is rejected when it is saved. The
frontend only shows the mTLS section when `TOKEN_EXCHANGE` is selected.

## Dependencies

### Backend

```kotlin
dependencies {
    // Valtimo 13.x
    implementation("com.ritense.valtimoplugins:http-client-authentication:2.1.0")
    // Valtimo 12.x
    implementation("com.ritense.valtimoplugins:http-client-authentication:2.1.1-V12")
}
```

### Frontend

```json
// Valtimo 13.x
{ "dependencies": { "@valtimo-plugins/http-client-authentication": "2.1.0" } }
// Valtimo 12.x
{ "dependencies": { "@valtimo-plugins/http-client-authentication": "2.1.1-V12" } }
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
            provide: PLUGINS_TOKEN,
            useValue: [
                httpClientAuthenticationPluginSpecification,
            ]
        }
    ]
})
```

## Configuration

| Property           | Type            | Required                | Description                                                            |
|--------------------|-----------------|-------------------------|------------------------------------------------------------------------|
| authenticationType | enum            | Yes                     | One of `NONE`, `BEARER`, `HEADER`, `TOKEN_EXCHANGE`                    |
| authHeaderName     | string          | For `HEADER`            | Name of the header that carries `authSecret`                          |
| authSecret         | string (secret) | For `BEARER` / `HEADER` | The bearer token, or the value of `authHeaderName`                    |
| tokenEndpoint      | string (URI)    | For `TOKEN_EXCHANGE`    | The Keycloak (or other OIDC) token endpoint URL                        |
| clientId           | string          | For `TOKEN_EXCHANGE`    | The client id used for both the client_credentials and exchange step  |
| clientSecret       | string (secret) | For `TOKEN_EXCHANGE`    | The client secret                                                      |
| audience           | string          | For `TOKEN_EXCHANGE`    | The audience the exchanged JWT is scoped to                           |
| scope              | string          | No                      | Optional OAuth2 scope for the client_credentials step                 |
| keystorePath       | string          | No, `TOKEN_EXCHANGE` only | Path to a JKS keystore file on disk; enables mTLS                   |
| keystoreSecret     | string (secret) | When `keystorePath` set | The keystore password                                                  |
| truststorePath     | string          | No, `TOKEN_EXCHANGE` only | Path to a JKS truststore file on disk                               |
| truststoreSecret   | string (secret) | When `truststorePath` set | The truststore password                                              |

The required fields are checked when a configuration is saved (created or updated). A configuration that misses a
field its type needs, or sets mTLS fields for a type other than `TOKEN_EXCHANGE`, is rejected.

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

1. Create a configuration of the Http Client Authentication plugin, choosing an authentication type:
   - `NONE` — no authentication header is added.
   - `BEARER` — sets the `Authorization: Bearer <authSecret>` header.
   - `HEADER` — sets a custom header named `authHeaderName` with value `authSecret`.
   - `TOKEN_EXCHANGE` — sets `Authorization: Bearer <jwt>` with a JWT obtained via Keycloak token-exchange.

   For `TOKEN_EXCHANGE`, optionally fill in the mTLS section to present a client certificate.
2. In a plugin that performs outbound REST calls, add a `@PluginProperty` of type `HttpClientAuthenticator` so a
   user can link a configured instance of this plugin to it.
3. Call `applyAuth(builder)` on the injected `HttpClientAuthenticator` before executing the outbound request.

## Upgrading to the new plugin key

As of `2.1.0` (Valtimo 13) and `2.1.1-V12` (Valtimo 12) the plugin key is `http-client-authentication`. Earlier
versions used `http-client-authentication-plugin`. Existing configurations are not migrated automatically; after
upgrading they still reference the old key, which no plugin provides anymore.

- Update the backend and frontend to matching versions. The frontend `pluginId` must equal the backend key.
- In autodeploy files (`*.pluginconfig.json`), change `pluginDefinitionKey` to `http-client-authentication`.
- Check that each configuration shows up under the new plugin. Recreate any that don't, and link them again in
  the plugins that use them.

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

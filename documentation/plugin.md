# Plugin Documentation

## Overview

The Http Client Authentication plugin provides authentication headers for outbound REST clients. Other plugins
depend on the `http-client-authentication` `@PluginCategory` and use a configured instance to apply either a bearer
token or a custom header to a Spring `RestClient.Builder` before making outbound calls.

## Dependencies

### Backend

```kotlin
dependencies {
    implementation("com.ritense.valtimoplugins:http-client-authentication:1.0.0")
}
```

### Frontend

```json
{
  "dependencies": {
    "@valtimo-plugins/http-client-authentication": "2.0.0"
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

| Property           | Type   | Required | Description                                                                             |
|---------------------|--------|----------|-------------------------------------------------------------------------------------------|
| authenticationType | enum   | Yes      | One of `NONE`, `BEARER`, `HEADER`. Determines which authentication header, if any, is set |
| authHeaderName     | string | Only for `HEADER` | Name of the header that will carry the secret when `authenticationType` is `HEADER` |
| authSecret         | string (secret) | Only for `BEARER`/`HEADER` | The bearer token, or the value set on `authHeaderName`                         |

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
2. In a plugin that performs outbound REST calls, add a `@PluginProperty` of type `HttpClientAuthenticator` so a
   user can link a configured instance of this plugin to it.
3. Call `applyAuth(builder)` on the injected `HttpClientAuthenticator` before executing the outbound request.

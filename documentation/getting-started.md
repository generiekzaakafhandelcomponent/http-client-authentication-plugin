# Getting Started

## Backend

```kotlin
dependencies {
    implementation("com.ritense.valtimoplugins:http-client-authentication:2.3.0-V12")
}
```

## Frontend

```json
{
  "dependencies": {
    "@valtimo-plugins/http-client-authentication": "2.3.0-V12"
  }
}
```

Use the exact versions. Register `HttpClientAuthenticationPluginModule` and `httpClientAuthenticationPluginSpecification`
in your `app.module.ts` as shown in the [Plugin](plugin.md#installation) documentation.

## Local development

See [Example Application](example-application.md) for running this plugin against a local GZAC instance.

For more information on how to build a plugin, see
the [Custom Plugin Definition](https://docs.valtimo.nl/features/plugins/plugins/custom-plugin-definition) documentation.

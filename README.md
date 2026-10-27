# Http Client Authentication Plugin

A GZAC plugin that authenticates outbound REST clients configured through other plugins, including the ZGW API
plugins: a bearer token, a custom header, an `Authorization: Token` header, a ZGW JWT from a client ID and secret or a
Keycloak token exchange, optionally combined with mutual TLS (PEM text or JKS/PKCS12 keystore files).
Built from the GZAC plugin template.

> **Supersedes `token-exchange-authentication`.** As of 2.1.0 the Keycloak token-exchange flow is part of this
> plugin as the `TOKEN_EXCHANGE` authentication type. The standalone `token-exchange-authentication` plugin is no
> longer maintained. See [Migrating from token-exchange-authentication](documentation/plugin.md#migrating-from-token-exchange-authentication).

## Supported Valtimo versions

| Valtimo | Branch | Backend version | Frontend version | Java | Angular |
|---------|--------|-----------------|------------------|------|---------|
| 13.x    | `main` | `2.1.0`         | `2.1.0`          | 21   | 19      |
| 12.x    | `v12`  | `2.2.1-V12`     | `2.2.1-V12`      | 17   | 17      |

The plugin key is `http-client-authentication` on Valtimo 12 and still `http-client-authentication-plugin` in `2.1.0`
for Valtimo 13; see the [release notes](documentation/release-notes.md) before upgrading from Valtimo 12 to 13.

## Getting started

Follow the [Getting Started](documentation/getting-started.md) guide for setup and development instructions.

## Documentation

- [Getting Started](documentation/getting-started.md) — setup and development instructions
- [Example Application](documentation/example-application.md) — running the example app locally
- [Plugin](documentation/plugin.md) — configuration and usage of the Http Client Authentication plugin
- [Release notes](documentation/release-notes.md) — versiegeschiedenis en wijzigingen

## Contact

Ayub Abdulkader (Ritense)

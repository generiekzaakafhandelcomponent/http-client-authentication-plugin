# Http Client Authentication Plugin

A GZAC plugin that authenticates the outbound REST and SOAP clients of other plugins: a bearer token, a custom header,
an `Authorization: Token` header, Basic credentials, a bearer token with `X-Api-Key`, a ZGW JWT from a client ID and
secret, a Keycloak token exchange or a token from a SAML token service, optionally combined with mutual TLS (client
certificate) and a custom trusted server certificate, as PEM text or as JKS/PKCS12 keystore files.

One configuration screen covers what otherwise takes a separate auth plugin each: plugins that select the ZGW API
authentication (Zaken, Documenten, Catalogi, Besluiten, Notificaties, Objecten, Objecttypen and Klantinteracties API),
`haal-centraal-auth`, `haal-centraal-authentication-plugin`, `suwinet-auth` (SOAP), `tokenauthentication`,
`token-exchange-authentication` or `mTLS-SSLContext` can select it instead. Those plugins keep working next to it.
Built from the GZAC plugin template.

## Getting started

Follow the [Getting Started](documentation/getting-started.md) guide for setup and development instructions.

## Documentation

- [Getting Started](documentation/getting-started.md) — setup and development instructions
- [Example Application](documentation/example-application.md) — running the example app locally
- [Plugin](documentation/plugin.md) — configuration and usage of the Http Client Authentication plugin
- [Release notes](documentation/release-notes.md) — versiegeschiedenis en wijzigingen

## Contact

Ayub Abdulkader (Ritense)

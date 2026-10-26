# Http Client Authentication Plugin

A GZAC plugin that authenticates outbound REST clients configured through other plugins: a bearer token, a custom
header, an `Authorization: Token` header, a ZGW JWT from a client ID and secret or a Keycloak token exchange,
optionally combined with mutual TLS (client certificate) and a custom trusted server certificate, as PEM text or as
JKS/PKCS12 keystore files. The ZGW API plugins (Zaken, Documenten, Catalogi,
Besluiten, Notificaties, Objecten, Objecttypen and Klantinteracties API) can select it as their authentication.
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

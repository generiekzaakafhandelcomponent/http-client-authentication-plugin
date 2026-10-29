# Http Client Authentication Plugin

Many GZAC plugins talk to an external system, such as Open Zaak, the Objecten API, Haal Centraal or Suwinet. This
plugin holds the login details for those systems in one place: a token, a key, a username and password, or a client
ID and secret, and when the system asks for it, a certificate.

You create a configuration once under **Admin > Plugins**, and select it in the plugin that talks to the external
system. The authentication plugins you may already use keep working next to it.

## Documentation

- [Http Client Authentication](documentation/plugin.md): creating a configuration, choosing the authentication type,
  certificates, and using it in other plugins
- [Release notes](documentation/release-notes.md): versiegeschiedenis en wijzigingen

## Contact

Ayub Abdulkader (Ritense)

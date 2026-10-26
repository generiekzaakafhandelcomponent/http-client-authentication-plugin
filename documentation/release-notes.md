# Release notes

Overzicht van wijzigingen per versie van de Http Client Authentication plugin.

## 2.1.0

De plugin kan nu als authenticatie gekozen worden in de Zaken, Documenten, Catalogi, Besluiten, Notificaties, Objecten,
Objecttypen en Klantinteracties API plugins, met de nieuwe authenticatietypes Token en Client ID / secret (ZGW JWT).
Daarnaast kunnen een clientcertificaat, private key en servercertificaat (PEM) ingevuld worden voor mutual TLS.

De plugin werkt met en zonder de ZGW modules en brengt ze niet mee: hij past zich aan de ZGW modules in de applicatie
aan. Er is niets in te stellen. Code die de plugin opzoekt via de klasse `HttpClientAuthenticationPlugin` moet
`HttpClientAuthenticator` of de plugin key gebruiken.

Ondersteuning voor Valtimo 13.51.0.

## 2.0.1

Ondersteuning voor Valtimo 13.48.0.

## 2.0.0

Compatibel met Valtimo 13.

## 1.0.0

Eerste opzet van de Http Client Authentication plugin. Compatibel met Valtimo 12.

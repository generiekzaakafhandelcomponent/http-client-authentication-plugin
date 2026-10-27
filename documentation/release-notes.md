# Release notes

Overzicht van wijzigingen per versie van de Http Client Authentication plugin.

## 2.1.0

Compatibel met Valtimo 13, met ondersteuning voor Valtimo 13.51.0.

### Nieuw

- **Authenticatie voor de ZGW API plugins.** De plugin kan als authenticatie gekozen worden in de Zaken, Documenten,
  Catalogi, Besluiten, Notificaties, Objecten, Objecttypen en Klantinteracties API plugins, met de nieuwe types
  **Token** en **Client ID / secret (ZGW JWT)**. De plugin werkt met en zonder de ZGW modules en brengt ze niet mee: hij
  past zich aan de ZGW modules in de applicatie aan.
- **Token exchange.** Het type **Token exchange** haalt bij Keycloak een token op met client credentials, wisselt het
  in voor een token voor de audience en stuurt dat als bearer token mee. Het token wordt hergebruikt tot kort voor het
  verloopt, ook door andere plugins met dezelfde configuratie. Dit is hetzelfde type als in `2.1.0-V12`/`2.1.1-V12`
  voor Valtimo 12, met dezelfde properties.
- **Certificaten (mutual TLS) voor elk type**, als PEM-tekst (clientcertificaat, private key, servercertificaat) of als
  JKS/PKCS12 keystore-bestanden (`keystorePath`, `keystoreSecret`, `truststorePath`, `truststoreSecret`). Bij token
  exchange gebruikt ook het token endpoint het clientcertificaat.

### Verbeteringen

- De credentials worden per request gezet, dus een verlopen token of een ZGW JWT van een andere gebruiker wordt nooit
  meegestuurd.
- Verbindingen met certificaten worden hergebruikt.
- Bij opslaan wordt de configuratie gecontroleerd en het foute veld genoemd. Een private key in een certificaatveld
  wordt geweigerd, omdat dat veld niet versleuteld opgeslagen wordt.

### Let op bij upgraden

- **Controle bij opstarten:** ook `*.pluginconfig.json` bestanden worden bij elke start gecontroleerd. Een configuratie
  die `2.0.1` accepteerde maar een verplicht veld mist (bijvoorbeeld een leeg `authSecret` bij `BEARER`), laat de
  applicatie niet meer starten.
- **Andere klasse:** de plugin-klasse is nu per applicatie gegenereerd. Code die de plugin opzoekt via de klasse
  `HttpClientAuthenticationPlugin` moet `HttpClientAuthenticator` of de plugin key gebruiken. Draai niet tegelijk
  nodes met `2.0.x` en `2.1.0` tegen dezelfde database (geen rolling upgrade).
- **Van Valtimo 12:** de Valtimo 12 versies gebruiken de key `http-client-authentication`, deze versie nog
  `http-client-authentication-plugin`. De configuraties van Valtimo 12 werken dus niet met deze versie;
  de versie die de key overneemt en bestaande configuraties meeneemt, is gepland als `3.0.0`.
- **Logging:** Valtimo logt request bodies op DEBUG (`LoggingRestClientCustomizer`). Bij token exchange staat daarin
  het client secret; zet die logging niet aan in productie.

## 2.0.1

Ondersteuning voor Valtimo 13.48.0.

## 2.0.0

Compatibel met Valtimo 13.

## 1.0.0

Eerste opzet van de Http Client Authentication plugin. Compatibel met Valtimo 12.

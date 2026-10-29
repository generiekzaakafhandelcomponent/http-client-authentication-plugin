# Release notes

Overzicht van wijzigingen per versie van de Http Client Authentication plugin.

## 3.1.0

Voor Valtimo 13.

### Nieuw

- **Eén plugin voor alle authenticatie.** Je kunt de plugin nu ook kiezen in **Haal Centraal**, **Suwinet** en andere
  plugins die om een authenticatie-configuratie vragen, zoals **Rotterdam Oracle EBS**. De authenticatie plugins die je
  daar al gebruikt blijven werken; je kiest zelf welke je configureert.
- **Nieuwe authenticatie types:** **Basic (username / password)**, **Bearer and X-Api-Key** (voor Haal Centraal) en
  **SAML token service** (voor Haal Centraal achter een SAML token service).
- **Duidelijker scherm.** De authenticatie types staan onder elkaar, met een korte uitleg van het gekozen type.

### Let op

- Een token, key of secret met een regeleinde of een bijzonder teken zoals `€` wordt niet meer geaccepteerd. Zulke
  secrets werkten ook voorheen niet.
- **Open Product** en de sjabloonlijst van **Xential** tonen deze plugin, maar kunnen hem niet gebruiken. Gebruik daar
  hun eigen authenticatie plugin.

## 3.0.0

Voor Valtimo 13.

- De plugin heet nu **Http Client Authentication**. Bestaande configuraties blijven werken.

## 2.1.0

Voor Valtimo 13.

### Nieuw

- **Authenticatie voor de ZGW API plugins.** Je kunt de plugin kiezen in de Zaken, Documenten, Catalogi, Besluiten,
  Notificaties, Objecten, Objecttypen en Klantinteracties API plugins, met de nieuwe types **Token** en **Client ID /
  secret (ZGW JWT)**.
- **Token exchange.** Voor systemen achter Keycloak, zoals Haal Centraal.
- **Certificaten (mutual TLS) bij elk type**, geplakt in het scherm (PEM) of als bestand op de server (keystore).

### Verbeteringen

- Bij opslaan wordt de configuratie gecontroleerd. Een private key in het veld voor het clientcertificaat wordt
  geweigerd, omdat dat veld niet versleuteld wordt opgeslagen.

## 2.0.1

Ondersteuning voor Valtimo 13.48.0.

## 2.0.0

Voor Valtimo 13.

## 1.0.0

Eerste versie van de Http Client Authentication plugin, voor Valtimo 12.

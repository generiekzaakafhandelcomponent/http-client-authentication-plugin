# Release notes

Overzicht van wijzigingen per versie van de Http Client Authentication plugin.

## 2.1.0 / 2.1.0-V12

`2.1.0` is compatibel met Valtimo 13, `2.1.0-V12` met Valtimo 12. Alleen toevoegingen; bestaande configuraties
blijven werken.

- Nieuw authenticatie type `TOKEN_EXCHANGE`: haalt een JWT op via een Keycloak client_credentials + token-exchange
  flow en stuurt die als bearer token mee. Het token wordt gecached tot kort voor het verloopt.
- Optionele mTLS (`keystorePath`, `keystoreSecret`, `truststorePath`, `truststoreSecret`) voor alle authenticatie
  types, ook `NONE`.
- Authenticatie wordt per request toegepast via een request interceptor in plaats van via default headers.
- Bij opslaan wordt gecontroleerd dat de velden die het gekozen type nodig heeft zijn ingevuld.
- De configuratie in de frontend toont alleen de velden van het gekozen type, plus een mTLS sectie. Duitse
  vertalingen toegevoegd.
- Vervangt de losse `token-exchange-authentication` plugin. Zie de
  [migratie-instructies](plugin.md#migrating-from-token-exchange-authentication).

## 2.0.0

Compatibel met Valtimo 13.

## 1.0.0

Eerste opzet van de Http Client Authentication plugin. Compatibel met Valtimo 12.

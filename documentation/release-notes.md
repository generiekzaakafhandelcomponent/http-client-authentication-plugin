# Release notes

Overzicht van wijzigingen per versie van de Http Client Authentication plugin.

## 2.2.0-V12

Compatibel met Valtimo 12. Bevat dezelfde functies als `2.1.0` voor Valtimo 13, onder de key
`http-client-authentication`.

### Nieuw

- **Authenticatie voor de ZGW API plugins.** De plugin kan als authenticatie gekozen worden in de Zaken, Documenten,
  Catalogi, Besluiten, Notificaties, Objecten en Objecttypen API plugins, met de nieuwe types **Token** en
  **Client ID / secret (ZGW JWT)**. De plugin werkt met en zonder de ZGW modules en brengt ze niet mee.
- **Certificaten (mutual TLS) voor elk type**, niet alleen voor token exchange: als PEM-tekst (clientcertificaat,
  private key, servercertificaat) of als JKS/PKCS12 keystore-bestanden. Bij token exchange gebruikt nu ook het token
  endpoint het clientcertificaat; daarbij worden naast het geconfigureerde servercertificaat ook de publiek vertrouwde
  certificaten vertrouwd.
- **Migratie van de oude key.** Configuraties met de key `http-client-authentication-plugin` (`2.1.0-V12` en
  `http-client-authentication-plugin` 1.0.0 uit de Plugins repository) gaan bij het opstarten over naar
  `http-client-authentication`. Zie [Upgrading from the old plugin key](plugin.md#upgrading-from-the-old-plugin-key).

### Verbeteringen

- Het token van token exchange wordt echt hergebruikt: voorheen hoorde het bij één plugin-instantie, die Valtimo per
  gebruik opnieuw maakt, zodat vrijwel elke call twee keer Keycloak aanriep.
- De credentials worden gezet met een request initializer. De request interceptor van `2.1.x` werd achter Valtimo's
  docker filter (profiel `dev`) genegeerd, waardoor er geen credentials meegingen.
- Verbindingen met certificaten worden hergebruikt, met Valtimo's connect timeouts.
- Bij opslaan wordt de configuratie gecontroleerd en het foute veld genoemd. Een private key in een certificaatveld
  wordt geweigerd, omdat dat veld niet versleuteld opgeslagen wordt.

### Let op bij upgraden

- **Truststore zonder keystore:** een `truststorePath` werd zonder `keystorePath` genegeerd. Nu wordt hij gebruikt
  voor de API calls, en is `truststoreSecret` verplicht.
- **Andere klasse:** de plugin-klasse is nu per applicatie gegenereerd. Code die de plugin opzoekt via de klasse
  `HttpClientAuthenticationPlugin` of `MtlsContextFactory` gebruikt moet `HttpClientAuthenticator` of de plugin key
  gebruiken. Stop alle nodes voor de upgrade (geen rolling upgrade).
- **npm:** gebruik de exacte versie `2.2.0-V12`; een range als `^2.2.0-V12` matcht ook Valtimo 13 versies. `-V12`
  versies krijgen voortaan de npm tag `v12`, niet `latest`.
- **Logging:** alle interceptors zien nu de credentials, en Valtimo logt request bodies op DEBUG
  (`LoggingRestClientCustomizer`), bij token exchange inclusief het client secret. De Socrates plugin logt request
  headers op DEBUG. Zet die logging niet aan in productie.
- **Van Valtimo 12 naar 13:** ga naar `3.0.0` (key `http-client-authentication`), niet naar `2.1.0`, dat de oude key
  nog gebruikt.

## 2.1.1-V12

Compatibel met Valtimo 12.

- **Breaking change:** de plugin key is gewijzigd van `http-client-authentication-plugin` naar
  `http-client-authentication`, en de titel van "Http Client Authentication Plugin" naar
  "Http Client Authentication". Bestaande configuraties werden niet automatisch gemigreerd; `2.2.0-V12` doet dat wel.

## 2.1.0-V12

`2.1.0-V12` is compatibel met Valtimo 12 en bevat alleen toevoegingen; bestaande configuraties blijven werken. (`2.1.0`
voor Valtimo 13 is een andere release, zie de release notes op `main`.)

- Nieuw authenticatie type `TOKEN_EXCHANGE`: haalt een JWT op via een Keycloak client_credentials + token-exchange
  flow en stuurt die als bearer token mee. Het token wordt gecached tot kort voor het verloopt.
- Optionele mTLS (`keystorePath`, `keystoreSecret`, `truststorePath`, `truststoreSecret`) voor het authenticatie
  type `TOKEN_EXCHANGE`. Een configuratie van een ander type met mTLS velden wordt bij opslaan geweigerd.
- Authenticatie wordt per request toegepast via een request interceptor in plaats van via default headers.
- Bij opslaan wordt gecontroleerd dat de velden die het gekozen type nodig heeft zijn ingevuld.
- De configuratie in de frontend toont alleen de velden van het gekozen type. Bij `TOKEN_EXCHANGE` staat daaronder
  een mTLS sectie. Duitse vertalingen toegevoegd.
- Vervangt de losse `token-exchange-authentication` plugin. Zie de
  [migratie-instructies](plugin.md#migrating-from-token-exchange-authentication).

## 2.0.0

Compatibel met Valtimo 13.

## 1.0.0

Eerste opzet van de Http Client Authentication plugin. Compatibel met Valtimo 12.

# Example Application

This project also contains a working example application which is meant to showcase the plugin.

## Running the example application

Run the commands below from the **project root** directory, except the frontend ones (from `frontend`).

### Prerequisites

- Java 21
- Node.js 20 (for example with [nvm](https://github.com/nvm-sh/nvm))
- [Docker (Desktop)](https://www.docker.com/products/docker-desktop/)

### Start docker

Make sure docker is running.

Start with gradle script:

```shell
./gradlew :backend:app:composeUp
```

### Start backend

By gradle script:

```shell
./gradlew :backend:app:bootRun
```

### Start frontend

```shell
cd frontend
nvm use 20
npm run clean
npm install
npm run build
npm start
```

### Keycloak users

The example application has a few test users that are preconfigured.

| Name         | Role           | Username  | Password  |
|--------------|----------------|-----------|-----------|
| James Vance  | ROLE_USER      | user      | user      |
| Asha Miller  | ROLE_ADMIN     | admin     | admin     |
| Morgan Finch | ROLE_DEVELOPER | developer | developer |

## Testing mutual TLS with the mTLS gateway

The docker compose setup contains `mtls-gateway`, an nginx gateway that only lets requests through when they present a
client certificate signed by the sandbox CA, like the gateway in front of the ZGW APIs at a municipality. It starts with
the `zgw` profile, the default for `composeUp`/`bootRun` (the `mtls` profile starts only the gateway, without the
APIs behind it).

| Gateway                  | Forwards to                             |
|--------------------------|-----------------------------------------|
| `https://127.0.0.1:8443` | Objecten API (`objecten-api`)           |
| `https://127.0.0.1:8444` | Open Zaak (`openzaak`)                  |
| `https://127.0.0.1:8445` | Open Notificaties (`open-notificaties`) |
| `https://localhost:8447` | Objecttypen API (`objecttypen-api`)     |

The example application autodeploys these configurations through the gateway
([mtls-gateway.pluginconfig.json](/backend/app/src/main/resources/config/plugin/mtls-gateway.pluginconfig.json)):

| Configuration                                     | Plugin                     | Settings                                                                          |
|---------------------------------------------------|----------------------------|-----------------------------------------------------------------------------------|
| Objects API Authentication via mTLS gateway       | Http Client Authentication | Token, client certificate and private key, sandbox CA as server certificate       |
| Objects API via mTLS gateway                      | Objecten API               | `https://127.0.0.1:8443/api/v2/`                                                  |
| Open Zaak Authentication via mTLS gateway         | Http Client Authentication | Client ID and secret (`valtimo_client`), same certificates                        |
| Catalogi API via mTLS gateway                     | Catalogi API               | `https://127.0.0.1:8444/catalogi/api/v1/`                                         |
| Zaken API via mTLS gateway                        | Zaken API                  | `https://127.0.0.1:8444/zaken/api/v1/`                                            |
| Documenten API via mTLS gateway                   | Documenten API             | `https://127.0.0.1:8444/documenten/api/v1/`, bronorganisatie `051845623`          |
| Open Notificaties Authentication via mTLS gateway | Http Client Authentication | Client ID and secret (`valtimo`), same certificates                               |
| Notificaties API via mTLS gateway                 | Notificaties API           | `https://127.0.0.1:8445/api/v1/`, callback `http://host.docker.internal:8080/...` |
| Objecttypes API Authentication via mTLS gateway   | Http Client Authentication | Token, same certificates                                                          |
| Objecttypes API via mTLS gateway                  | Objecttypen API            | `https://localhost:8447/api/v2/`                                                  |

Both object management configurations, *Bomen via mTLS gateway* and *Verzoeken*, use the Objects API and the
Objecttypes API through the gateway, so the *Objects → Bomen via mTLS gateway* menu shows them only when the client
certificate is presented. The zaaktypes of the Catalogi API via the gateway appear next to the regular ones, with
`https://127.0.0.1:8444` URLs.

### Verzoek through the mTLS gateway

The Verzoek plugin turns a verzoek object into an *Example* case. Every call Valtimo makes for it goes through the
gateway:

1. [create-verzoek-example.http](/backend/app/examples/create-verzoek-example.http) creates a verzoek in the Objects
   API through `8443`. Run it with the `mtls` environment, which presents the client certificate.
2. The Objects API notifies Open Notificaties (kanaal `objecten`), which calls Valtimo back, directly (see below).
3. Valtimo fetches the verzoek through `8443`, creates an *Example* case, and starts `create-zaakdossier`. This process
   creates the zaak and the initiator rol in Open Zaak through `8444`, so the zaak lives at a
   `https://127.0.0.1:8444/zaken/...` URL.

### Notificaties through the mTLS gateway

Valtimo reaches Open Notificaties through `8445` with *Notificaties API via mTLS gateway*: it creates kanalen and
registers its abonnement there, as usual. The Verzoek plugin uses this configuration.

Open Notificaties delivers notificaties to Valtimo directly, at `http://host.docker.internal:8080`. Routing that
direction through the gateway too means Open Notificaties presents its own certificate, which an administrator adds to
the abonnement. Valtimo then has to accept an abonnement it didn't register itself, which needs Valtimo 13.52 or later.

The example application has no plain *Notificaties API (Autodeployed)* configuration. Valtimo registers an abonnement
per Notificaties API configuration and removes abonnementen with its callback URL that it doesn't know. A plain and a
gateway configuration for the same Open Notificaties would remove each other's abonnement: they see the same callback
URL, under two different abonnement URLs.

### Objecttypen API through the mTLS gateway

The objecttype URL is an identifier, not only an address. Valtimo builds it from the URL of the Objecttypes API
configuration and sends it to the Objects API: as `type` when it creates an object, as the `type` filter when it lists
objects, and in the `objectType` filter of the verzoek abonnement. The Objects API only accepts objecttype URLs under
the address it knows the Objecttypen API by, and puts that same URL in its notificaties.

So the Objects API reaches the Objecttypen API through the gateway as well, at the same URL as Valtimo:
`https://localhost:8447/api/v2/`. Its *Objecttypen API via mTLS gateway* service
([demodata.json](/backend/app/imports/objects-api/fixtures/demodata.json)) presents its own client certificate
(`certs/objecten-api.pem`, `CN=objecten-api-sandbox-client`) and trusts the sandbox CA. Inside docker, `localhost` is
the container itself, so `objecten-api-objecttypen-relay` forwards `localhost:8447` in the Objects API's container to
the gateway; TLS passes through it unchanged. At a municipality, Valtimo and the Objects API reach the gateway under the
same host name and don't need this.

Objecttype URLs under `http://host.docker.internal:8011` are no longer accepted by the Objects API.

### Documents through the mTLS gateway

The *Example* case has a *Documents* tab, and Valtimo's upload process (`document-upload`) is linked to it
([document-upload.process-link.json](/backend/app/src/main/resources/config/global/process-link/document-upload.process-link.json)):

| Step                    | Plugin action                    | Configuration                   |
|-------------------------|----------------------------------|---------------------------------|
| `upload-document`       | `store-uploaded-document`        | Documenten API via mTLS gateway |
| `link-document-to-zaak` | `link-uploaded-document-to-zaak` | Zaken API via mTLS gateway      |

Open an *Example* case created from a verzoek, so it has a zaak, and upload a document on the *Documents* tab. Valtimo
stores it in Open Zaak and links it to the zaak, both through `8444`. Listing, downloading, editing and deleting the
document go through the gateway as well.

Valtimo picks the Documenten API configuration for a document by the start of the document's URL. Open Zaak builds its
URLs from the host it was called on, and the gateway rewrites them to `https://127.0.0.1:8444`, so the documents of a
zaak reached through the gateway have `https://127.0.0.1:8444/documenten/...` URLs. Only *Documenten API via mTLS
gateway* matches them; without that configuration the *Documents* tab, download and delete fail with
`Could not find DocumentenApiPlugin configuration for informatieobjectUrl`. Open Zaak accepts these URLs as its own
documents when they are linked to a zaak. The `inhoud` (download) and `bestandsdelen` (uploads in parts) URLs in its
responses are rewritten too, so those calls also go through the gateway.

The plain *Zaken API (Autodeployed)*, *Catalogi API (Autodeployed)* and *Documenten API (Autodeployed)*
configurations stay next to the gateway ones, on the same Open Zaak under `http://localhost:8001`. Valtimo asks every
Catalogi API configuration for the zaaktype list, so each zaaktype shows twice; deleting a document and searching a
zaak by identificatie also ask every Zaken API configuration.

The gateway logs every request with the client certificate it received and the authentication scheme (never the
secret):

```shell
docker logs -f plugin-docker-compose-mtls-gateway
# 192.168.65.1 "GET /api/v2/objects?... HTTP/1.1" 200 client="CN=valtimo-sandbox-client" verify=SUCCESS authorization="Token"
```

A request without a client certificate is refused with `400 No required SSL certificate was sent`. To try this from
the configuration screen, edit *Objects API Authentication via mTLS gateway*: clear the client certificate (calls now
fail), or paste a different certificate or key (saving is refused).

To call the gateway by hand:

```shell
cd backend/app/imports/mtls-gateway/certs
curl --cacert ca.pem --cert client.pem --key client-key.pem \
  -H 'Authorization: Token 182c13e2209161852c53cef53a879f7a2f923430' https://127.0.0.1:8443/api/v2/objects
```

The certificates in [imports/mtls-gateway/certs](/backend/app/imports/mtls-gateway/certs) are throwaway test material.
[generate-certs.sh](/backend/app/imports/mtls-gateway/generate-certs.sh) creates new ones and rewrites
`mtls-gateway.pluginconfig.json` to match. Valtimo's docker filter (`valtimo.docker.filter`) only rewrites
`http://localhost` URLs, so it leaves the `https://` gateway URLs alone, including `https://localhost:8447`.

## Source code

The source code is split up into two modules:

1. [Frontend](/frontend)
2. [Backend](/backend)

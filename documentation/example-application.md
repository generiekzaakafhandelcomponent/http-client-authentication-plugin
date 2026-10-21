# Example Application

This project also contains a working example application which is meant to showcase the plugin.

## Running the example application

All commands below should be run from the **project root** directory.

### Prerequisites

- Java 21
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
the `zgw` profile (the default for `composeUp`/`bootRun`), or on its own with `--profile mtls`.

| Gateway                  | Forwards to                   |
|--------------------------|-------------------------------|
| `https://127.0.0.1:8443` | Objecten API (`objecten-api`) |
| `https://127.0.0.1:8444` | Open Zaak (`openzaak`)        |

The example application autodeploys these configurations through the gateway
([mtls-gateway.pluginconfig.json](/backend/app/src/main/resources/config/plugin/mtls-gateway.pluginconfig.json)):

| Configuration                               | Plugin                     | Settings                                                                    |
|---------------------------------------------|----------------------------|-----------------------------------------------------------------------------|
| Objects API Authentication via mTLS gateway | Http Client Authentication | Token, client certificate and private key, sandbox CA as server certificate |
| Objects API via mTLS gateway                | Objecten API               | `https://127.0.0.1:8443/api/v2/`                                            |
| Open Zaak Authentication via mTLS gateway   | Http Client Authentication | Client ID and secret (`valtimo_client`), same certificates                  |
| Catalogi API via mTLS gateway               | Catalogi API               | `https://127.0.0.1:8444/catalogi/api/v1/`                                   |

The object management configuration *Bomen via mTLS gateway* reads its objects through the gateway, so the
*Objects → Bomen via mTLS gateway* menu shows them only when the client certificate is presented. The zaaktypes of the
Catalogi API via the gateway appear next to the regular ones, with `https://127.0.0.1:8444` URLs.

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
`mtls-gateway.pluginconfig.json` to match. The gateway URLs use `127.0.0.1` rather than `localhost`, because Valtimo's
docker filter (`valtimo.docker.filter`) rewrites `localhost` to `host.docker.internal` for outgoing calls.

## Source code

The source code is split up into two modules:

1. [Frontend](/frontend)
2. [Backend](/backend)

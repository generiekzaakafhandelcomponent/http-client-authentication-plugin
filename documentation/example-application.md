# Example Application

This project also contains a working example application which is meant to showcase the plugin.

## Running the example application

Run the commands below from the **project root** directory, except the frontend ones (from `frontend`).

### Prerequisites

- Java 17
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

### Example configurations

The example application autodeploys a configuration of every simple type
([http-client-authentication.pluginconfig.json](/backend/app/src/main/resources/config/plugin/http-client-authentication.pluginconfig.json)):
*Http Client Authentication - None*, *- Bearer*, *- Header* and *- Token exchange*. They show the properties of each
type. *Token exchange* is a placeholder: the sandbox Keycloak has no client set up for token exchange, so a call with it
fails with `401 Unauthorized`.

## Testing mutual TLS with the mTLS gateway

The docker compose setup contains `mtls-gateway`, an nginx gateway that only lets requests through when they present a
client certificate signed by the sandbox CA, like the gateway in front of the ZGW APIs at a municipality. It starts with
the `zgw` profile, the default for `composeUp`/`bootRun` (the `mtls` profile starts only the gateway, without the
APIs behind it). In this example the
Objecten API is behind it, at `https://127.0.0.1:8443`.

The example application autodeploys
([mtls-gateway.pluginconfig.json](/backend/app/src/main/resources/config/plugin/mtls-gateway.pluginconfig.json)):

| Configuration                               | Plugin                     | Settings                                                                    |
|---------------------------------------------|----------------------------|-----------------------------------------------------------------------------|
| Objects API Authentication via mTLS gateway | Http Client Authentication | Token, client certificate and private key, sandbox CA as server certificate |
| Objects API via mTLS gateway                | Objecten API               | `https://127.0.0.1:8443/api/v2/`                                            |

The object management configuration *Bomen via mTLS gateway*
([boom-mtls-gateway.json](/backend/app/src/main/resources/config/objectmanagement/boom-mtls-gateway.json)) reads the
Boom objects through the gateway, so **Objects → Bomen via mTLS gateway** lists them only because the configuration
presents the client certificate. Remove the certificates from *Objects API Authentication via mTLS gateway* (turn off
**Certificates (mutual TLS)** and save) and the list stays empty: without the sandbox CA as server certificate, GZAC
doesn't trust the gateway, which logs a failed handshake. To see the gateway refuse GZAC instead
(`400 No required SSL certificate was sent`), clear only the client certificate and its private key. To undo this,
turn the certificates on again and paste `client.pem`, `client-key.pem` and `ca.pem` from
[imports/mtls-gateway/certs](/backend/app/imports/mtls-gateway/certs), or restart the backend, which autodeploys the
configuration again.

The gateway logs every request with the client certificate it received and the authentication scheme (never the
secret):

```shell
docker logs -f plugin-docker-compose-mtls-gateway
# 192.168.65.1 "GET /api/v2/objects?... HTTP/1.1" 200 client="CN=valtimo-sandbox-client" verify=SUCCESS authorization="Token"
```

A request without a client certificate is refused with `400 No required SSL certificate was sent`. To call the gateway
by hand:

```shell
cd backend/app/imports/mtls-gateway/certs
curl --cacert ca.pem --cert client.pem --key client-key.pem \
  -H 'Authorization: Token 182c13e2209161852c53cef53a879f7a2f923430' https://127.0.0.1:8443/api/v2/objects
```

The certificates in [imports/mtls-gateway/certs](/backend/app/imports/mtls-gateway/certs) are throwaway test material.
[generate-certs.sh](/backend/app/imports/mtls-gateway/generate-certs.sh) creates new ones and rewrites
`mtls-gateway.pluginconfig.json` to match. The gateway also has routes for Open Zaak (`8444`), Open Notificaties
(`8445`) and the Objecttypen API (`8447`), which this example doesn't use.

Autodeployment adds and updates configurations but never removes them: a sandbox database from an earlier version of
this example may still hold configurations that the files no longer contain. Delete those under
**Admin > Configuration > Plugins**.

The docker compose project of this example (`gzac-docker-compose`, containers `plugin-docker-compose-*`) has the same
name as in other plugin repositories built from the same template. Stop another repository's example application
before you start this one: `composeUp` would otherwise recreate its containers.

## Source code

The source code is split up into two modules:

1. [Frontend](/frontend)
2. [Backend](/backend)

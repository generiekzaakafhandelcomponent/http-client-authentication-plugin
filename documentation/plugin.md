# Http Client Authentication

## What it does

Many plugins in GZAC talk to an external system, such as Open Zaak, the Objecten API, Haal Centraal or Suwinet. That
system wants to know who is calling. The Http Client Authentication plugin holds those login details in one place: a
password, token or key, and, when the system asks for it, a certificate.

You create a configuration of this plugin once, and then select it in the plugin that talks to the external system.

You can select it in:

- the **Zaken, Documenten, Catalogi, Besluiten, Notificaties, Objecten, Objecttypen and Klantinteracties API** plugins
- **Haal Centraal** (BRP / BAG)
- **Suwinet**
- other plugins that ask for an authentication configuration, such as **Rotterdam Oracle EBS**

The authentication plugins you may already use for these keep working. You choose which one to configure.

## Creating a configuration

1. Go to **Admin > Plugins** and click **Configure plugin**.
2. Choose **Http Client Authentication**.
3. Fill in a **Configuration name**, for example *Open Zaak login*.
4. Choose the **Authentication type** (see below) and fill in its fields.
5. Turn on **Certificates (mutual TLS)** only when the external system asks for a certificate (see below).
6. Click **Save configuration**.

The button only becomes available when everything required is filled in correctly. Fields marked *(required)* must be
filled in.

## Choosing the authentication type

Ask the supplier or administrator of the external system which type it expects. Below the type you choose, the screen
shows a short explanation.

| Authentication type              | Typically used for                               | What you fill in                                       |
|----------------------------------|--------------------------------------------------|--------------------------------------------------------|
| **Bearer**                       | Systems that give you a fixed token              | **Authentication secret**: the token                   |
| **Token**                        | Objecten, Objecttypen and Klantinteracties API   | **Authentication secret**: the API token               |
| **Header**                       | Systems that give you an API key                 | **Authentication header name** (for example `X-Api-Key`) and **Authentication secret** |
| **Bearer and X-Api-Key**         | Haal Centraal                                    | **Authentication secret**                              |
| **Basic (username / password)**  | Suwinet and other systems with a username and password | **Username** and **Password**                    |
| **Client ID / secret (ZGW JWT)** | Open Zaak, Open Notificaties and other ZGW APIs  | **Client ID** and **Client secret** (at least 32 characters) |
| **Token exchange**               | Systems behind Keycloak, such as Haal Centraal   | **Token endpoint URL**, **Client ID**, **Client secret** and **Audience**; **Scope** is optional |
| **SAML token service**           | Haal Centraal behind a SAML token service        | **SAML token service URL**; the other fields are optional |
| **None**                         | Systems without a login                          | Nothing                                                |

Tips:

- **Client ID / secret (ZGW JWT)**: use the client ID and secret that are registered for GZAC in Open Zaak or Open
  Notificaties.
- **Token exchange**: the supplier gives you the token endpoint URL (it starts with `https://`) and the audience.
- **SAML token service**: fill in the complete URL you received, ending in `?b64=true`, for example
  `https://sts.example.com/sts/requestsecuritytoken/v1?b64=true`. Without `?b64=true` calls fail. Fill in **Header for
  the token** only when the supplier names one.
- **Username** can't contain a colon (`:`).
- A token, key or secret can't contain line breaks or special characters such as `€`.

## Certificates (mutual TLS)

Some systems only accept calls from GZAC when it shows a certificate. Turn on **Certificates (mutual TLS)** when the
supplier asks for this, or when they tell you their system uses its own (not publicly trusted) certificate.

Under **Certificates as**, choose where the certificates come from. Ask your administrator which one applies.

### PEM

You paste the certificates as text into the screen. Each one starts with a line like `-----BEGIN CERTIFICATE-----`.
Paste the whole text, including that first and last line.

- **Client certificate (PEM)**: the certificate GZAC shows to the external system.
- **Client certificate private key (PEM)**: the private key that belongs to that certificate. It starts with
  `-----BEGIN PRIVATE KEY-----`. A key protected with a password can't be used.
- **Server certificate (PEM, optional)**: only needed when the external system uses its own certificate. When you
  fill it in, GZAC trusts only that certificate for this configuration.

Don't paste the private key in the client certificate field. The screen won't let you save it.

### Keystore (JKS / PKCS12)

The certificates are in files on the GZAC server. Your administrator places them there and tells you where they are.

- **Keystore path** and **Keystore password**: the file with the client certificate and its key.
- **Truststore path** and **Truststore password**: optional, the file with the certificates to trust. A truststore
  needs its password.

GZAC only opens these files when it makes its first call. A wrong path or password doesn't show up when saving, but as
an error on that first call.

### Turning certificates off

Turn the toggle off and save. The certificates are removed. To use them again later, fill them in again, including
the private key or passwords.

## Using the configuration in another plugin

1. Open the configuration of the plugin that talks to the external system, for example **Zaken API** or **Objecten
   API**, under **Admin > Plugins**.
2. In its authentication field, select the configuration you created.
3. Save.

Which type to choose per plugin:

| Plugin                                    | Authentication type                                                |
|-------------------------------------------|--------------------------------------------------------------------|
| Zaken, Documenten, Catalogi, Besluiten and Notificaties API | **Client ID / secret (ZGW JWT)**                 |
| Objecten, Objecttypen and Klantinteracties API | **Token**                                                     |
| Haal Centraal                             | **Bearer and X-Api-Key**, **Header**, **Token exchange** or **SAML token service**, as the supplier tells you |
| Suwinet                                   | **Basic (username / password)**, **Header** or **None**; for a certificate, any type except **SAML token service**, with certificates as keystore |
| Rotterdam Oracle EBS                      | Any type except **SAML token service**, with certificates          |

Several plugins can use the same configuration.

Some plugins show this plugin in their list but can't use it: **Open Product**, and the template list of **Xential**.
Use their own authentication plugin there.

## Editing a configuration

Passwords, secrets and private keys are not shown again after saving. When you edit a configuration, leave those
fields empty to keep what is stored. Fill them in again when you:

- switch to another authentication type
- paste a new client certificate (fill in its private key too)
- change a keystore or truststore path (fill in its password too)

## When saving fails

The screen checks most things itself and keeps **Save configuration** unavailable until they are right. Some things
are only checked by the server, for example whether the private key belongs to the client certificate. If the server
refuses, you see "An unexpected error occurred" with a reference ID. Pass that ID on to your administrator: it tells
them which field is wrong.

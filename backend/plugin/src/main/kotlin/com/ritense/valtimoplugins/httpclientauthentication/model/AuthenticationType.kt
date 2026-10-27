package com.ritense.valtimoplugins.httpclientauthentication.model

enum class AuthenticationType {
    NONE,
    BEARER,
    HEADER,
    TOKEN,
    ZGW_JWT,
    TOKEN_EXCHANGE,
    BASIC,
    BEARER_AND_API_KEY,
    SAML_TOKEN,
}

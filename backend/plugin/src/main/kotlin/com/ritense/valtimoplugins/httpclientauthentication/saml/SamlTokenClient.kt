/*
 * Copyright 2015-2026 Ritense BV, the Netherlands.
 *
 * Licensed under EUPL, Version 1.2 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.ritense.valtimoplugins.httpclientauthentication.saml

import com.ritense.valtimoplugins.httpclientauthentication.exception.SamlTokenException
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.body
import java.net.URI

/**
 * Requests a SAML token from a WS-Trust token service, as `haal-centraal-authentication-plugin` does: a fixed
 * RequestSecurityToken for a SAML 2.0 assertion, whose response body (base64 with `?b64=true`) is the token. The URL is
 * called as configured.
 */
class SamlTokenClient(
    private val restClientBuilder: RestClient.Builder,
) {
    fun token(
        tokenServiceUrl: URI,
        requestFactory: ClientHttpRequestFactory,
    ): String {
        val body =
            try {
                restClientBuilder
                    .clone()
                    .requestFactory(requestFactory)
                    .build()
                    .post()
                    .uri(tokenServiceUrl)
                    .contentType(TEXT_XML)
                    .header(SOAP_ACTION_HEADER, ISSUE_ACTION)
                    .body(REQUEST_SECURITY_TOKEN)
                    .retrieve()
                    .body<String>()
            } catch (e: RestClientResponseException) {
                // Not the message: it holds the response body
                throw SamlTokenException(
                    "The SAML token service at $tokenServiceUrl answered with HTTP ${e.statusCode.value()}",
                    e,
                )
            } catch (e: RestClientException) {
                throw SamlTokenException("Could not reach the SAML token service at $tokenServiceUrl", e)
            }
        val token = body?.trim()
        // Sent as a header value; the response isn't echoed, it may be an assertion
        if (token.isNullOrEmpty() || !token.all { it in '!'..'~' }) {
            throw SamlTokenException(
                "The SAML token service at $tokenServiceUrl did not return a single-line token " +
                    "(does the URL ask for base64, e.g. ?b64=true?)",
            )
        }
        return token
    }

    companion object {
        private val TEXT_XML = MediaType("text", "xml", Charsets.UTF_8)
        private const val SOAP_ACTION_HEADER = "SOAPAction"
        private const val ISSUE_ACTION = "http://docs.oasis-open.org/ws-sx/ws-trust/200512/RST/Issue"
        private val REQUEST_SECURITY_TOKEN =
            """
            <soapenv:Envelope
                xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                xmlns:ns="http://docs.oasis-open.org/ws-sx/ws-trust/200512">
                <soapenv:Header
                    xmlns:wsa="http://www.w3.org/2005/08/addressing">
                    <wsa:Action>http://docs.oasis-open.org/ws-sx/ws-trust/200512/RST/Issue</wsa:Action>
                </soapenv:Header>
                <soapenv:Body>
                    <ns:RequestSecurityToken>
                        <ns:RequestType>http://docs.oasis-open.org/ws-sx/ws-trust/200512/Issue</ns:RequestType>
                        <ns:TokenType>urn:oasis:names:tc:SAML:2.0:assertion</ns:TokenType>
                    </ns:RequestSecurityToken>
                </soapenv:Body>
            </soapenv:Envelope>
            """.trimIndent()
    }
}

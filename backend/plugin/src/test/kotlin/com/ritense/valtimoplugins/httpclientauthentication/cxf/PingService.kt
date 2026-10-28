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

package com.ritense.valtimoplugins.httpclientauthentication.cxf

import jakarta.jws.Oneway
import jakarta.jws.WebMethod
import jakarta.jws.WebService
import org.apache.cxf.endpoint.Client
import org.apache.cxf.frontend.ClientProxy
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.apache.cxf.message.Message
import org.apache.cxf.transport.http.HTTPConduit
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy

@WebService(targetNamespace = "http://example.com/ping")
interface PingService {
    @Oneway
    @WebMethod
    fun ping(message: String)
}

/** A real CXF (SOAP) client, set up the way suwinet-plugin's SuwinetSOAPClient does before it calls applyAuth. */
fun soapClient(url: String): Pair<PingService, Client> {
    val service =
        JaxWsProxyFactoryBean()
            .apply {
                serviceClass = PingService::class.java
                address = url
            }.create() as PingService
    val client = ClientProxy.getClient(service)
    // An immutable header map and a client policy with timeouts
    client.requestContext[Message.PROTOCOL_HEADERS] = mapOf("Expect" to listOf("100-continue"))
    (client.conduit as HTTPConduit).client =
        HTTPClientPolicy().apply {
            connectionTimeout = 5_000
            receiveTimeout = 5_000
            isAllowChunking = true
        }
    return service to client
}

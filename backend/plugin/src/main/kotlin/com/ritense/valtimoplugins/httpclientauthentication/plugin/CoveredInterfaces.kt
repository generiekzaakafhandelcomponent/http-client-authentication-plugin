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

package com.ritense.valtimoplugins.httpclientauthentication.plugin

import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * A category interface of another plugin that this plugin implements when it is on the classpath.
 *
 * @param interfaceName the interface that carries the `@PluginCategory`
 * @param implementedBy what the plugin class implements: the interface itself when the base class has all its methods,
 * otherwise a support interface that extends it
 * @param requires the classes the implementation needs besides the interface
 */
data class CoveredInterface(
    val interfaceName: String,
    val implementedBy: String = interfaceName,
    val requires: List<String> = emptyList(),
)

object CoveredInterfaces {
    private const val SUPPORT = "com.ritense.valtimoplugins.httpclientauthentication.support"
    private val REACTOR_NETTY = listOf("reactor.netty.http.client.HttpClient", "io.netty.handler.ssl.SslContextBuilder")
    private val CXF =
        listOf(
            "org.apache.cxf.endpoint.Client",
            "org.apache.cxf.phase.AbstractPhaseInterceptor",
            "org.apache.cxf.transport.http.HTTPConduit",
        )

    val ALL =
        listOf(
            CoveredInterface("com.ritense.zakenapi.ZakenApiAuthentication"),
            CoveredInterface("com.ritense.documentenapi.DocumentenApiAuthentication"),
            CoveredInterface("com.ritense.catalogiapi.CatalogiApiAuthentication"),
            CoveredInterface("com.ritense.besluitenapi.BesluitenApiAuthentication"),
            CoveredInterface("com.ritense.notificatiesapi.NotificatiesApiAuthentication"),
            CoveredInterface("com.ritense.objectenapi.ObjectenApiAuthentication"),
            CoveredInterface("com.ritense.objecttypenapi.ObjecttypenApiAuthentication"),
            CoveredInterface("com.ritense.klantinteractiesapi.KlantinteractiesApiAuthentication"),
            CoveredInterface("com.ritense.tokenauthentication.plugin.TokenAuthentication"),
            CoveredInterface(
                "com.ritense.valtimoplugins.haalcentraalauth.HaalCentraalAuthentication",
                "$SUPPORT.HaalCentraalAuthSupport",
                REACTOR_NETTY,
            ),
            CoveredInterface(
                "com.ritense.valtimoplugins.haalcentraalauthenticationplugin.HaalCentraalAuthentication",
                "$SUPPORT.HaalCentraalAuthenticationPluginSupport",
                REACTOR_NETTY,
            ),
            CoveredInterface(
                "com.ritense.valtimoplugins.suwinetauth.plugin.SuwinetAuth",
                "$SUPPORT.SuwinetAuthSupport",
                CXF,
            ),
            CoveredInterface(
                "com.ritense.valtimoplugins.tokenexchangeauth.TokenExchangeAuthentication",
                "$SUPPORT.TokenExchangeAuthenticationSupport",
            ),
            CoveredInterface(
                "com.ritense.valtimoplugins.mtlssslcontext.MTlsSslContext",
                "$SUPPORT.MTlsSslContextSupport",
            ),
        )

    private val logger = KotlinLogging.logger {}

    /** The interfaces to implement: those of [covered] on the classpath, with what they require. */
    fun present(
        isPresent: (String) -> Boolean,
        covered: List<CoveredInterface> = ALL,
    ): List<String> =
        covered
            .filter { isPresent(it.interfaceName) }
            .filter { coveredInterface ->
                val missing = coveredInterface.requires.filterNot(isPresent)
                if (missing.isNotEmpty()) {
                    logger.warn {
                        "Not offering ${coveredInterface.interfaceName}: the classes it needs are missing: " +
                            missing.joinToString()
                    }
                }
                missing.isEmpty()
            }.map { it.implementedBy }
}

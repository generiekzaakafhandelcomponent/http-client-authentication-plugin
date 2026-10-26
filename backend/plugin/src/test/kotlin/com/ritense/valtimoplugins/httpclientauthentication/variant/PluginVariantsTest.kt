/*
 * Copyright 2026 Ritense BV, the Netherlands.
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
 */

package com.ritense.valtimoplugins.httpclientauthentication.variant

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class PluginVariantsTest {
    @Test
    fun `should select the variant without ZGW interfaces when no ZGW module is present`() {
        val variant = PluginVariants.select { false }

        assertEquals("0000000", variant.bits)
        assertEquals("valtimoplugins.httpclientauthentication.variant.z0000000", variant.packageName)
        assertEquals(
            "valtimoplugins.httpclientauthentication.variant.z0000000.HttpClientAuthenticationPlugin",
            variant.className,
        )
        assertEquals(emptyList(), variant.zgwAuthenticationInterfaces)
    }

    @Test
    fun `should set one bit per ZGW interface on the classpath, in a fixed order`() {
        val present =
            setOf(
                "com.ritense.zakenapi.ZakenApiAuthentication",
                "com.ritense.objectenapi.ObjectenApiAuthentication",
                "com.ritense.objecttypenapi.ObjecttypenApiAuthentication",
            )

        val variant = PluginVariants.select { it in present }

        assertEquals("1000011", variant.bits)
        assertEquals(
            listOf(
                "com.ritense.zakenapi.ZakenApiAuthentication",
                "com.ritense.objectenapi.ObjectenApiAuthentication",
                "com.ritense.objecttypenapi.ObjecttypenApiAuthentication",
            ),
            variant.zgwAuthenticationInterfaces,
        )
    }

    @Test
    fun `should give the bits in a fixed order of ZGW APIs`() {
        assertEquals(
            listOf(
                "com.ritense.zakenapi.ZakenApiAuthentication",
                "com.ritense.documentenapi.DocumentenApiAuthentication",
                "com.ritense.catalogiapi.CatalogiApiAuthentication",
                "com.ritense.besluitenapi.BesluitenApiAuthentication",
                "com.ritense.notificatiesapi.NotificatiesApiAuthentication",
                "com.ritense.objectenapi.ObjectenApiAuthentication",
                "com.ritense.objecttypenapi.ObjecttypenApiAuthentication",
            ),
            PluginVariants.select { true }.zgwAuthenticationInterfaces,
        )
    }

    @Test
    fun `should select the variant with every ZGW interface when all ZGW modules are present`() {
        assertEquals("1111111", PluginVariants.selected.bits)
    }
}

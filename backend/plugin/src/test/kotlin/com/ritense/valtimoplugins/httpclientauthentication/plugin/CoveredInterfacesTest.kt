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

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class CoveredInterfacesTest {
    private val covered =
        listOf(
            CoveredInterface("a.Interface"),
            CoveredInterface("b.Interface", "b.Support", listOf("b.Needed")),
            CoveredInterface("c.Interface", "c.Support", listOf("c.Needed", "c.AlsoNeeded")),
        )

    @Test
    fun `should implement the interfaces on the classpath, through their support interface`() {
        val classpath = setOf("a.Interface", "b.Interface", "b.Needed")

        assertEquals(listOf("a.Interface", "b.Support"), CoveredInterfaces.present(classpath::contains, covered))
    }

    @Test
    fun `should skip an interface whose implementation needs classes that are missing`() {
        val classpath = setOf("b.Interface", "c.Interface", "c.Needed")

        assertEquals(emptyList(), CoveredInterfaces.present(classpath::contains, covered))
    }

    @Test
    fun `should implement nothing when no covered interface is on the classpath`() {
        assertEquals(emptyList(), CoveredInterfaces.present({ false }, covered))
    }

    @Test
    fun `should name existing support interfaces`() {
        CoveredInterfaces.ALL.filter { it.implementedBy != it.interfaceName }.forEach {
            Class.forName(
                it.implementedBy,
            )
        }
    }
}

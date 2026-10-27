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

package com.ritense.valtimoplugins.httpclientauthentication.deployment

import com.ritense.plugin.domain.PluginDefinition
import com.ritense.plugin.repository.PluginDefinitionRepository
import com.ritense.valtimoplugins.httpclientauthentication.plugin.HttpClientAuthenticationPluginBase.Companion.PLUGIN_KEY
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.Optional
import kotlin.test.assertEquals

internal class PluginDefinitionCheckTest {
    @Test
    fun `should accept the definition of the generated class`() {
        check(deployedClassName = PLUGIN_CLASS).checkDeployedClass()
    }

    @Test
    fun `should stop the application when another class is deployed`() {
        val exception = assertThrows<IllegalStateException> { check("other.Plugin").checkDeployedClass() }

        assertEquals(
            "Plugin 'http-client-authentication' is deployed with class other.Plugin instead of $PLUGIN_CLASS: " +
                "another library declares the same plugin key, or this node doesn't deploy plugins " +
                "(valtimo.bootstrap.enabled) and another version deployed it",
            exception.message,
        )
    }

    @Test
    fun `should stop the application when nothing is deployed`() {
        assertThrows<IllegalStateException> { check(deployedClassName = null).checkDeployedClass() }
    }

    private fun check(deployedClassName: String?): PluginDefinitionCheck {
        val definition = deployedClassName?.let { PluginDefinition(PLUGIN_KEY, "title", "description", it) }
        val repository =
            mock<PluginDefinitionRepository> {
                on { findById(PLUGIN_KEY) } doReturn Optional.ofNullable(definition)
            }
        return PluginDefinitionCheck(repository, PLUGIN_CLASS)
    }

    companion object {
        private const val PLUGIN_CLASS = "com.example.GeneratedPlugin"
    }
}

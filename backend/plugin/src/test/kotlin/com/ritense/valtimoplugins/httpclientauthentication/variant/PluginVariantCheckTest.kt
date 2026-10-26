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

import com.ritense.plugin.domain.PluginDefinition
import com.ritense.plugin.repository.PluginDefinitionRepository
import com.ritense.valtimoplugins.httpclientauthentication.plugin.HttpClientAuthenticationPluginBase.Companion.PLUGIN_KEY
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.Optional
import kotlin.test.assertEquals

internal class PluginVariantCheckTest {
    private val variant = PluginVariant("0000011")

    @Test
    fun `should accept the definition of the selected variant`() {
        check(deployedClassName = variant.className).checkDeployedVariant()
    }

    @Test
    fun `should stop the application when another variant is deployed`() {
        val other = PluginVariant("1111111")

        val exception = assertThrows<IllegalStateException> { check(other.className).checkDeployedVariant() }

        assertEquals(
            "Plugin 'http-client-authentication' is deployed with class ${other.className}, but the ZGW " +
                "modules on the classpath need ${variant.className}. Of " +
                "valtimoplugins.httpclientauthentication.variant, Valtimo may only scan ${variant.packageName}: " +
                "check valtimo.annotation-scan.accepted-packages and the package of the @SpringBootApplication class.",
            exception.message,
        )
    }

    @Test
    fun `should stop the application when no variant is deployed`() {
        assertThrows<IllegalStateException> { check(deployedClassName = null).checkDeployedVariant() }
    }

    private fun check(deployedClassName: String?): PluginVariantCheck {
        val definition =
            deployedClassName?.let { PluginDefinition(PLUGIN_KEY, "title", "description", it) }
        val repository =
            mock<PluginDefinitionRepository> {
                on { findById(PLUGIN_KEY) } doReturn Optional.ofNullable(definition)
            }
        return PluginVariantCheck(repository, variant)
    }
}

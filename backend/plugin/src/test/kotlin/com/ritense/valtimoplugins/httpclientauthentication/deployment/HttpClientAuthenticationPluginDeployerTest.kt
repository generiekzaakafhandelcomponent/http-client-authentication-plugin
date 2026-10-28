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

import com.ritense.plugin.domain.PluginCategory
import com.ritense.plugin.domain.PluginDefinition
import com.ritense.plugin.repository.PluginCategoryRepository
import com.ritense.plugin.repository.PluginDefinitionRepository
import com.ritense.valtimoplugins.httpclientauthentication.plugin.GeneratedPluginClass
import com.ritense.valtimoplugins.httpclientauthentication.plugin.HttpClientAuthenticationPluginBase.Companion.PLUGIN_KEY
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import othercopy.SameKeyPlugin
import java.util.Optional
import kotlin.test.assertEquals

internal class HttpClientAuthenticationPluginDeployerTest {
    @Test
    fun `should refuse to replace a definition that Valtimo deployed for a scanned class with the same key`() {
        val repository = repositoryWith(SameKeyPlugin::class.java.name)

        val exception = assertThrows<IllegalStateException> { deployer(repository).deploy() }

        assertEquals(
            "Plugin 'http-client-authentication' is also declared by othercopy.SameKeyPlugin, " +
                "which Valtimo deployed: " +
                "remove the library that contains it (e.g. another copy of http-client-authentication)",
            exception.message,
        )
        verify(repository, never()).save(any())
    }

    @Test
    fun `should replace the definition of an earlier version, whose class no longer exists`() {
        val repository =
            repositoryWith("valtimoplugins.httpclientauthentication.variant.z11111111.HttpClientAuthenticationPlugin")

        deployer(repository).deploy()

        verify(repository).save(any())
    }

    private fun repositoryWith(className: String) =
        mock<PluginDefinitionRepository> {
            on { findById(PLUGIN_KEY) } doReturn
                Optional.of(PluginDefinition(PLUGIN_KEY, "title", "description", className))
        }

    private fun deployer(repository: PluginDefinitionRepository) =
        HttpClientAuthenticationPluginDeployer(
            repository,
            mock<PluginCategoryRepository> {
                on { findById(any()) } doReturn Optional.empty()
                on { save(any<PluginCategory>()) } doAnswer { it.getArgument(0) }
            },
            GeneratedPluginClass.get(),
        )
}

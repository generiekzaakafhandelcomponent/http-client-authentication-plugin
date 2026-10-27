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

import com.ritense.valtimoplugins.httpclientauthentication.variant.PluginVariantEnvironmentPostProcessor.Companion.ACCEPTED_PACKAGES
import com.ritense.valtimoplugins.httpclientauthentication.variant.PluginVariantEnvironmentPostProcessor.Companion.PROPERTY_SOURCE_NAME
import org.junit.jupiter.api.Test
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class PluginVariantEnvironmentPostProcessorTest {
    private val postProcessor = PluginVariantEnvironmentPostProcessor()
    private val variant = PluginVariant("0000011")

    @Test
    fun `should add the variant package when the application configured no packages`() {
        val environment = environment(emptyMap())

        postProcessor.addVariantPackage(environment, variant)

        assertEquals(listOf(variant.packageName), acceptedPackages(environment))
    }

    @Test
    fun `should keep comma-separated packages of the application`() {
        val environment = environment(mapOf(ACCEPTED_PACKAGES to "nl.gemeente.app, com.example"))

        postProcessor.addVariantPackage(environment, variant)

        assertEquals(listOf("nl.gemeente.app", "com.example", variant.packageName), acceptedPackages(environment))
    }

    @Test
    fun `should keep packages the application configured as a list`() {
        val environment =
            environment(mapOf("$ACCEPTED_PACKAGES[0]" to "nl.gemeente.app", "$ACCEPTED_PACKAGES[1]" to "com.example"))

        postProcessor.addVariantPackage(environment, variant)

        assertEquals(listOf("nl.gemeente.app", "com.example", variant.packageName), acceptedPackages(environment))
    }

    @Test
    fun `should leave the environment alone when the variant package is configured already`() {
        val environment = environment(mapOf(ACCEPTED_PACKAGES to "nl.gemeente.app,${variant.packageName}"))

        postProcessor.addVariantPackage(environment, variant)

        assertNull(environment.propertySources.get(PROPERTY_SOURCE_NAME))
    }

    @Test
    fun `should add the package once when run twice`() {
        val environment = environment(mapOf(ACCEPTED_PACKAGES to "nl.gemeente.app"))

        postProcessor.addVariantPackage(environment, variant)
        postProcessor.addVariantPackage(environment, variant)

        assertEquals(listOf("nl.gemeente.app", variant.packageName), acceptedPackages(environment))
    }

    private fun environment(properties: Map<String, Any>) =
        StandardEnvironment().apply {
            propertySources.addLast(MapPropertySource("application", properties))
        }

    // The way Valtimo's AnnotatedClassResolver reads it
    private fun acceptedPackages(environment: StandardEnvironment) =
        environment.getProperty(ACCEPTED_PACKAGES, Array<String>::class.java)!!.map { it.trim() }
}

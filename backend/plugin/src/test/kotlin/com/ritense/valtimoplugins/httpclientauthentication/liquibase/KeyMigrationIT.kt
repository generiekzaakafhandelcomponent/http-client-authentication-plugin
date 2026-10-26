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

package com.ritense.valtimoplugins.httpclientauthentication.liquibase

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.ritense.objectenapi.ObjectenApiPlugin
import com.ritense.plugin.domain.PluginConfiguration
import com.ritense.plugin.domain.PluginConfigurationId
import com.ritense.plugin.service.PluginService
import com.ritense.valtimo.contract.config.LiquibaseRunner
import com.ritense.valtimoplugins.httpclientauthentication.BaseIntegrationTest
import com.ritense.valtimoplugins.httpclientauthentication.plugin.HttpClientAuthenticationPluginBase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import kotlin.test.assertEquals

// Not transactional: Liquibase runs on its own connection
internal class KeyMigrationIT : BaseIntegrationTest() {
    @Autowired
    lateinit var liquibaseRunner: LiquibaseRunner

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var pluginService: PluginService

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private val createdConfigurations = mutableListOf<PluginConfigurationId>()

    @AfterEach
    fun removeConfigurations() {
        createdConfigurations.reversed().forEach { pluginService.deletePluginConfiguration(it) }
    }

    @Test
    fun `should move configurations of the old key next to the new one, keeping secrets and references`() {
        val authentication = createAuthentication()
        val objectenApi =
            create(
                "objectenapi",
                mapOf(
                    "url" to "https://objecten.example.com/api/v2/",
                    "authenticationPluginConfiguration" to authentication.id.id.toString(),
                ),
            )
        copyDefinition(NEW_KEY, OLD_KEY)
        moveConfiguration(authentication.id, OLD_KEY)

        liquibaseRunner.run()

        assertEquals(NEW_KEY, keyOf(authentication.id))
        assertEquals(0, definitionCount(OLD_KEY))
        val plugin = pluginService.createInstance(authentication.id) as HttpClientAuthenticationPluginBase
        assertEquals("secret-bearer-token", plugin.authSecret)
        val objecten = pluginService.createInstance(objectenApi.id) as ObjectenApiPlugin
        assertEquals(
            authentication.id,
            (objecten.authenticationPluginConfiguration as HttpClientAuthenticationPluginBase).configurationId,
        )
    }

    @Test
    fun `should create the new key when only the old one exists, and do nothing when run again`() {
        val authentication = createAuthentication()
        val definition = definitionRow(NEW_KEY)
        val properties = propertyRows(NEW_KEY)
        val categories = categoryRows(NEW_KEY)
        copyDefinition(NEW_KEY, OLD_KEY)
        jdbcTemplate.update(
            "UPDATE plugin_configuration SET plugin_definition_key = ? WHERE plugin_definition_key = ?",
            OLD_KEY,
            NEW_KEY,
        )
        deleteDefinition(NEW_KEY)

        liquibaseRunner.run()
        liquibaseRunner.run()

        assertEquals(definition, definitionRow(NEW_KEY))
        assertEquals(properties, propertyRows(NEW_KEY))
        assertEquals(categories, categoryRows(NEW_KEY))
        assertEquals(0, definitionCount(OLD_KEY))
        assertEquals(NEW_KEY, keyOf(authentication.id))
        assertEquals(
            "secret-bearer-token",
            (pluginService.createInstance(authentication.id) as HttpClientAuthenticationPluginBase).authSecret,
        )
    }

    @Test
    fun `should leave a database without the old key alone`() {
        val authentication = createAuthentication()
        val definitions = jdbcTemplate.queryForList("SELECT * FROM plugin_definition ORDER BY plugin_definition_key")

        liquibaseRunner.run()

        assertEquals(
            definitions,
            jdbcTemplate.queryForList("SELECT * FROM plugin_definition ORDER BY plugin_definition_key"),
        )
        assertEquals(NEW_KEY, keyOf(authentication.id))
    }

    private fun createAuthentication() =
        create(
            NEW_KEY,
            mapOf(
                "authenticationType" to "BEARER",
                "authSecret" to "secret-bearer-token",
            ),
        )

    private fun create(
        key: String,
        properties: Map<String, String>,
    ): PluginConfiguration =
        pluginService
            .createPluginConfiguration(
                "Migration ${UUID.randomUUID()}",
                objectMapper.valueToTree<ObjectNode>(properties),
                key,
            ).also { createdConfigurations.add(it.id) }

    private fun copyDefinition(
        from: String,
        to: String,
    ) {
        jdbcTemplate.update(
            "INSERT INTO plugin_definition (plugin_definition_key, title, description, class_name) " +
                "SELECT ?, title, description, class_name FROM plugin_definition WHERE plugin_definition_key = ?",
            to,
            from,
        )
        jdbcTemplate.update(
            "INSERT INTO plugin_property (plugin_property_key, plugin_definition_key, title, required, secret, " +
                "field_name, field_type) SELECT plugin_property_key, ?, title, required, secret, field_name, " +
                "field_type FROM plugin_property WHERE plugin_definition_key = ?",
            to,
            from,
        )
        jdbcTemplate.update(
            "INSERT INTO plugin_definition_category (plugin_definition_key, plugin_category_key) " +
                "SELECT ?, plugin_category_key FROM plugin_definition_category WHERE plugin_definition_key = ?",
            to,
            from,
        )
    }

    private fun deleteDefinition(key: String) {
        jdbcTemplate.update("DELETE FROM plugin_property WHERE plugin_definition_key = ?", key)
        jdbcTemplate.update("DELETE FROM plugin_definition_category WHERE plugin_definition_key = ?", key)
        jdbcTemplate.update("DELETE FROM plugin_definition WHERE plugin_definition_key = ?", key)
    }

    private fun moveConfiguration(
        id: PluginConfigurationId,
        key: String,
    ) {
        jdbcTemplate.update(
            "UPDATE plugin_configuration SET plugin_definition_key = ? WHERE plugin_configuration_id = ?",
            key,
            id.id,
        )
    }

    private fun keyOf(id: PluginConfigurationId): String =
        jdbcTemplate.queryForObject(
            "SELECT plugin_definition_key FROM plugin_configuration WHERE plugin_configuration_id = ?",
            String::class.java,
            id.id,
        )!!

    private fun definitionCount(key: String): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM plugin_definition WHERE plugin_definition_key = ?",
            Int::class.java,
            key,
        )!!

    private fun definitionRow(key: String) =
        jdbcTemplate.queryForMap(
            "SELECT title, description, class_name FROM plugin_definition WHERE plugin_definition_key = ?",
            key,
        )

    private fun propertyRows(key: String) =
        jdbcTemplate.queryForList(
            "SELECT plugin_property_key, title, required, secret, field_name, field_type FROM plugin_property " +
                "WHERE plugin_definition_key = ? ORDER BY plugin_property_key",
            key,
        )

    private fun categoryRows(key: String) =
        jdbcTemplate.queryForList(
            "SELECT plugin_category_key FROM plugin_definition_category WHERE plugin_definition_key = ? ORDER BY plugin_category_key",
            key,
        )

    companion object {
        private const val NEW_KEY = "http-client-authentication"
        private const val OLD_KEY = "http-client-authentication-plugin"
    }
}

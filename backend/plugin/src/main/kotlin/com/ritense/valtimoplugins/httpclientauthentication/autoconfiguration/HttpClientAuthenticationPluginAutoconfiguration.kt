package com.ritense.valtimoplugins.httpclientauthentication.autoconfiguration

import com.ritense.plugin.repository.PluginCategoryRepository
import com.ritense.plugin.repository.PluginDefinitionRepository
import com.ritense.plugin.service.PluginService
import com.ritense.valtimo.contract.client.ValtimoHttpRestClientConfigurationProperties
import com.ritense.valtimo.contract.config.LiquibaseMasterChangeLogLocation
import com.ritense.valtimoplugins.httpclientauthentication.deployment.HttpClientAuthenticationPluginDeployer
import com.ritense.valtimoplugins.httpclientauthentication.deployment.PluginDefinitionCheck
import com.ritense.valtimoplugins.httpclientauthentication.plugin.HttpClientAuthenticationPluginFactory
import com.ritense.valtimoplugins.httpclientauthentication.saml.SamlTokenClient
import com.ritense.valtimoplugins.httpclientauthentication.tls.TlsRequestFactoryCache
import com.ritense.valtimoplugins.httpclientauthentication.tokenexchange.TokenExchangeClient
import com.ritense.valtimoplugins.httpclientauthentication.tokenexchange.TokenExchangeTokenCache
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.web.client.RestClient

// Bean names are prefixed: other auth plugins define beans such as tokenExchangeClient too
@AutoConfiguration
class HttpClientAuthenticationPluginAutoconfiguration {
    @Bean
    @ConditionalOnMissingBean(HttpClientAuthenticationPluginFactory::class)
    fun httpClientAuthenticationPluginFactory(
        pluginService: PluginService,
        tlsRequestFactoryCache: TlsRequestFactoryCache,
        tokenExchangeTokenCache: TokenExchangeTokenCache,
        samlTokenClient: SamlTokenClient,
    ): HttpClientAuthenticationPluginFactory =
        HttpClientAuthenticationPluginFactory(
            pluginService,
            tlsRequestFactoryCache,
            tokenExchangeTokenCache,
            samlTokenClient,
        )

    @Bean
    @ConditionalOnMissingBean(TokenExchangeClient::class)
    fun httpClientAuthenticationTokenExchangeClient(restClientBuilder: RestClient.Builder): TokenExchangeClient =
        TokenExchangeClient(restClientBuilder)

    @Bean
    @ConditionalOnMissingBean(TokenExchangeTokenCache::class)
    fun httpClientAuthenticationTokenExchangeTokenCache(
        tokenExchangeClient: TokenExchangeClient,
    ): TokenExchangeTokenCache = TokenExchangeTokenCache(tokenExchangeClient)

    @Bean
    @ConditionalOnMissingBean(SamlTokenClient::class)
    fun httpClientAuthenticationSamlTokenClient(restClientBuilder: RestClient.Builder): SamlTokenClient =
        SamlTokenClient(restClientBuilder)

    // Moves configurations of the old key http-client-authentication-plugin; after Valtimo's own changelogs (no order)
    @Bean
    fun httpClientAuthenticationLiquibaseChangeLogLocation(): LiquibaseMasterChangeLogLocation =
        LiquibaseMasterChangeLogLocation("config/liquibase/http-client-authentication-master.xml")

    // Under the same condition as Valtimo's PluginDeploymentListener
    @Bean
    @ConditionalOnProperty(
        prefix = "valtimo.bootstrap",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    @ConditionalOnMissingBean(HttpClientAuthenticationPluginDeployer::class)
    fun httpClientAuthenticationPluginDeployer(
        pluginDefinitionRepository: PluginDefinitionRepository,
        pluginCategoryRepository: PluginCategoryRepository,
    ): HttpClientAuthenticationPluginDeployer =
        HttpClientAuthenticationPluginDeployer(pluginDefinitionRepository, pluginCategoryRepository)

    @Bean
    @ConditionalOnMissingBean(PluginDefinitionCheck::class)
    fun httpClientAuthenticationPluginDefinitionCheck(
        pluginDefinitionRepository: PluginDefinitionRepository,
    ): PluginDefinitionCheck = PluginDefinitionCheck(pluginDefinitionRepository)

    @Bean
    @ConditionalOnMissingBean(TlsRequestFactoryCache::class)
    fun httpClientAuthenticationTlsRequestFactoryCache(
        restClientProperties: ObjectProvider<ValtimoHttpRestClientConfigurationProperties>,
    ): TlsRequestFactoryCache =
        TlsRequestFactoryCache(
            restClientProperties.getIfAvailable {
                ValtimoHttpRestClientConfigurationProperties()
            },
        )
}

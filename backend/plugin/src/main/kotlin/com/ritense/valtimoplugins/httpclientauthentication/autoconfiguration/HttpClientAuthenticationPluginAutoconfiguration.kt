package com.ritense.valtimoplugins.httpclientauthentication.autoconfiguration

import com.ritense.plugin.repository.PluginDefinitionRepository
import com.ritense.plugin.service.PluginService
import com.ritense.valtimo.contract.client.ValtimoHttpRestClientConfigurationProperties
import com.ritense.valtimoplugins.httpclientauthentication.plugin.HttpClientAuthenticationPluginFactory
import com.ritense.valtimoplugins.httpclientauthentication.tls.TlsRequestFactoryCache
import com.ritense.valtimoplugins.httpclientauthentication.tokenexchange.TokenExchangeClient
import com.ritense.valtimoplugins.httpclientauthentication.tokenexchange.TokenExchangeTokenCache
import com.ritense.valtimoplugins.httpclientauthentication.variant.PluginVariantCheck
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.web.client.RestClient

@AutoConfiguration
class HttpClientAuthenticationPluginAutoconfiguration {
    @Bean
    @ConditionalOnMissingBean(HttpClientAuthenticationPluginFactory::class)
    fun httpClientAuthenticationPluginFactory(
        pluginService: PluginService,
        tlsRequestFactoryCache: TlsRequestFactoryCache,
        tokenExchangeTokenCache: TokenExchangeTokenCache,
    ): HttpClientAuthenticationPluginFactory =
        HttpClientAuthenticationPluginFactory(pluginService, tlsRequestFactoryCache, tokenExchangeTokenCache)

    @Bean
    @ConditionalOnMissingBean(TokenExchangeClient::class)
    fun tokenExchangeClient(restClientBuilder: RestClient.Builder): TokenExchangeClient =
        TokenExchangeClient(restClientBuilder)

    @Bean
    @ConditionalOnMissingBean(TokenExchangeTokenCache::class)
    fun httpClientAuthenticationTokenExchangeTokenCache(
        tokenExchangeClient: TokenExchangeClient,
    ): TokenExchangeTokenCache = TokenExchangeTokenCache(tokenExchangeClient)

    @Bean
    @ConditionalOnMissingBean(PluginVariantCheck::class)
    fun httpClientAuthenticationPluginVariantCheck(
        pluginDefinitionRepository: PluginDefinitionRepository,
    ): PluginVariantCheck = PluginVariantCheck(pluginDefinitionRepository)

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

package com.ritense.valtimoplugins.httpclientauthentication.autoconfiguration

import com.ritense.plugin.service.PluginService
import com.ritense.valtimoplugins.httpclientauthentication.plugin.HttpClientAuthenticationPluginFactory
import com.ritense.valtimoplugins.httpclientauthentication.tokenexchange.TokenExchangeClient
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.web.client.RestClient

@AutoConfiguration
class HttpClientAuthenticationPluginAutoconfiguration {

    @Bean
    @ConditionalOnMissingBean(TokenExchangeClient::class)
    fun tokenExchangeClient(restClientBuilder: RestClient.Builder): TokenExchangeClient =
        TokenExchangeClient(restClientBuilder)

    @Bean
    @ConditionalOnMissingBean(HttpClientAuthenticationPluginFactory::class)
    fun httpClientAuthenticationPluginFactory(
        pluginService: PluginService,
        tokenExchangeClient: TokenExchangeClient,
    ): HttpClientAuthenticationPluginFactory {
        return HttpClientAuthenticationPluginFactory(pluginService, tokenExchangeClient)
    }
}

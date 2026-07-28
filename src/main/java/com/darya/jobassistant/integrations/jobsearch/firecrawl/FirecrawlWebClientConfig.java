package com.darya.jobassistant.integrations.jobsearch.firecrawl;

import com.darya.jobassistant.config.FirecrawlProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * A dedicated {@code WebClient} for Firecrawl, separate from the shared {@code WebClient} bean in
 * {@code WebClientConfig}. Firecrawl needs its own base URL and connect/read timeouts
 * ({@link FirecrawlProperties}), which would otherwise mean mutating the global {@code
 * WebClientProperties} settings every other integration (RemoteOK, OpenAI) relies on. Only
 * created when {@code firecrawl.enabled=true}, so no Firecrawl-specific bean - and no connection
 * pool - exists while the integration stays off.
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "firecrawl", name = "enabled", havingValue = "true")
public class FirecrawlWebClientConfig {

    private final FirecrawlProperties firecrawlProperties;

    @Bean
    public WebClient firecrawlWebClient(ObjectMapper objectMapper) {
        return WebClient.builder()
                .baseUrl(firecrawlProperties.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(buildHttpClient()))
                .exchangeStrategies(buildExchangeStrategies(objectMapper))
                .build();
    }

    private HttpClient buildHttpClient() {
        Duration connectTimeout = firecrawlProperties.connectTimeout();
        Duration readTimeout = firecrawlProperties.readTimeout();
        int connectTimeoutMs = Math.toIntExact(connectTimeout.toMillis());

        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(readTimeout)
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(readTimeout.toMillis(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(readTimeout.toMillis(), TimeUnit.MILLISECONDS)));
    }

    private ExchangeStrategies buildExchangeStrategies(ObjectMapper objectMapper) {
        return ExchangeStrategies.builder()
                .codecs(configurer -> {
                    configurer.defaultCodecs().jackson2JsonEncoder(
                            new Jackson2JsonEncoder(objectMapper, MediaType.APPLICATION_JSON));
                    configurer.defaultCodecs().jackson2JsonDecoder(
                            new Jackson2JsonDecoder(objectMapper, MediaType.APPLICATION_JSON));
                })
                .build();
    }
}

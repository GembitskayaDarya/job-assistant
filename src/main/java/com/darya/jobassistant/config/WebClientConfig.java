package com.darya.jobassistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Shared WebClient for outbound HTTP calls to third-party integrations
 * (job boards, AI providers, etc). Kept here rather than under
 * {@code integrations/} because it's infrastructure, not a port.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "cookie", "set-cookie", "x-api-key", "api-key");

    private final WebClientProperties webClientProperties;

    @Bean
    public WebClient webClient(ObjectMapper objectMapper) {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(buildHttpClient()))
                .exchangeStrategies(buildExchangeStrategies(objectMapper))
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    private HttpClient buildHttpClient() {
        ConnectionProvider connectionProvider = ConnectionProvider.builder("web-client-pool")
                .maxConnections(webClientProperties.maxConnections())
                .maxIdleTime(Duration.ofMillis(webClientProperties.maxIdleTimeMs()))
                .maxLifeTime(Duration.ofMillis(webClientProperties.maxLifeTimeMs()))
                .pendingAcquireTimeout(Duration.ofMillis(webClientProperties.pendingAcquireTimeoutMs()))
                .evictInBackground(Duration.ofMillis(webClientProperties.evictInBackgroundMs()))
                .build();

        int timeoutMs = Math.toIntExact(webClientProperties.timeoutMs());

        return HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMs)
                .responseTimeout(Duration.ofMillis(webClientProperties.timeoutMs()))
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS)));
    }

    private ExchangeStrategies buildExchangeStrategies(ObjectMapper objectMapper) {
        return ExchangeStrategies.builder()
                .codecs(configurer -> {
                    configurer.defaultCodecs().jackson2JsonEncoder(
                            new Jackson2JsonEncoder(objectMapper, MediaType.APPLICATION_JSON));
                    configurer.defaultCodecs().jackson2JsonDecoder(
                            new Jackson2JsonDecoder(objectMapper, MediaType.APPLICATION_JSON));
                    configurer.defaultCodecs().maxInMemorySize(webClientProperties.maxInMemorySizeBytes());
                })
                .build();
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            if (log.isDebugEnabled()) {
                log.debug("WebClient request: {} {}", request.method(), request.url());
                request.headers().forEach((name, values) -> log.debug("{}: {}", name, maskIfSensitive(name, values)));
            }
            return Mono.just(request);
        });
    }

    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            if (log.isDebugEnabled()) {
                log.debug("WebClient response: {}", response.statusCode());
            }
            return Mono.just(response);
        });
    }

    private Object maskIfSensitive(String headerName, Object values) {
        return SENSITIVE_HEADERS.contains(headerName.toLowerCase()) ? "***" : values;
    }
}
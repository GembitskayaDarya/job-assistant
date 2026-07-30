package com.darya.jobassistant.config;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "telegram", name = "enabled", havingValue = "true")
public class TelegramBotConfig {

    private final TelegramProperties telegramProperties;

    /**
     * Long-polling/command-reply client - unchanged default OkHttp transport (including {@code
     * retryOnConnectionFailure=true}), since a dropped long-polling GET for the next update batch
     * is always safe to silently retry and has no user-visible business action tied to it.
     * {@code @Primary} so the two existing unqualified injection points ({@code
     * JobAssistantTelegramBot}, {@code TelegramMessageSender}) keep resolving to this bean
     * unchanged.
     */
    @Bean
    @Primary
    public TelegramClient telegramClient() {
        return new OkHttpTelegramClient(telegramProperties.token());
    }

    /**
     * Dedicated client for {@link com.darya.jobassistant.integrations.notifier.telegram.TelegramJobNotificationAdapter}
     * (both the legacy monitoring send and the automatic-recommendation compact send) - same bot
     * token, deliberately different transport posture: {@code retryOnConnectionFailure(false)}
     * guarantees one processing attempt never contains a hidden OkHttp replay of a message-send
     * request (see Sprint 8 Step 11A.1). The bounded {@link TelegramProperties#notificationSendTimeout()}
     * (connect/read/write alike) replaces OkHttp's own unconfigurable 10-second defaults with an
     * explicit, project-configured bound.
     */
    @Bean
    @Qualifier("notificationTelegramClient")
    public TelegramClient notificationTelegramClient() {
        Duration timeout = telegramProperties.notificationSendTimeout();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .retryOnConnectionFailure(false)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .build();
        return new OkHttpTelegramClient(httpClient, telegramProperties.token());
    }
}

package com.darya.jobassistant.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "telegram", name = "enabled", havingValue = "true")
public class TelegramBotConfig {

    private final TelegramProperties telegramProperties;

    @Bean
    public TelegramClient telegramClient() {
        return new OkHttpTelegramClient(telegramProperties.token());
    }
}

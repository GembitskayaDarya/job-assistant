package com.darya.jobassistant.config;

import com.darya.jobassistant.telegram.JobAssistantTelegramBot;
import com.darya.jobassistant.tracking.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "telegram", name = "enabled", havingValue = "true")
public class TelegramBotConfig {

    private final TelegramProperties telegramProperties;
    private final ApplicationService applicationService;

    @Bean
    public JobAssistantTelegramBot jobAssistantTelegramBot() {
        return new JobAssistantTelegramBot(
                telegramProperties.token(),
                telegramProperties.username(),
                applicationService);
    }

    @Bean
    public TelegramBotsApi telegramBotsApi(JobAssistantTelegramBot jobAssistantTelegramBot) throws TelegramApiException {
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        telegramBotsApi.registerBot(jobAssistantTelegramBot);
        return telegramBotsApi;
    }
}

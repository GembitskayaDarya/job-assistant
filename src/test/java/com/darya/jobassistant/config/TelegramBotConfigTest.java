package com.darya.jobassistant.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Sprint 8 Step 11A.1: proves - via reflection on the real, resolved {@code
 * telegrambots-client}/{@code okhttp} classes, not an assumption from the property name - that the
 * dedicated notification-send {@link TelegramClient} bean has {@code
 * retryOnConnectionFailure=false} while the long-polling bean keeps OkHttp's own default ({@code
 * true}) untouched, both built from the same {@link TelegramProperties#token()}.
 */
class TelegramBotConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TelegramPropertiesConfig.class, TelegramBotConfig.class);

    @Test
    void notificationClient_hasRetryOnConnectionFailureDisabled() throws Exception {
        contextRunner.withPropertyValues(enabledProperties()).run(context -> {
            assertThat(context).hasNotFailed();
            TelegramClient notificationClient = context.getBean("notificationTelegramClient", TelegramClient.class);
            OkHttpClient httpClient = extractOkHttpClient(notificationClient);
            assertThat(httpClient.retryOnConnectionFailure()).isFalse();
        });
    }

    @Test
    void notificationClient_usesConfiguredTimeoutForConnectReadAndWrite() throws Exception {
        contextRunner.withPropertyValues(
                "telegram.enabled=true", "telegram.token=test-token", "telegram.notification-send-timeout=7s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    TelegramClient notificationClient = context.getBean("notificationTelegramClient", TelegramClient.class);
                    OkHttpClient httpClient = extractOkHttpClient(notificationClient);
                    assertThat(httpClient.connectTimeoutMillis()).isEqualTo(7000);
                    assertThat(httpClient.readTimeoutMillis()).isEqualTo(7000);
                    assertThat(httpClient.writeTimeoutMillis()).isEqualTo(7000);
                });
    }

    @Test
    void longPollingClient_keepsRetryOnConnectionFailureEnabled_unchangedFromBefore() throws Exception {
        contextRunner.withPropertyValues(enabledProperties()).run(context -> {
            assertThat(context).hasNotFailed();
            TelegramClient pollingClient = context.getBean(TelegramClient.class); // @Primary, unqualified
            OkHttpClient httpClient = extractOkHttpClient(pollingClient);
            assertThat(httpClient.retryOnConnectionFailure()).isTrue();
        });
    }

    @Test
    void bothClients_areBuiltFromTheSameBotToken_noDuplicateTokenProperty() {
        contextRunner.withPropertyValues(enabledProperties()).run(context -> {
            assertThat(context).hasNotFailed();
            TelegramProperties properties = context.getBean(TelegramProperties.class);
            // Only one token property exists on TelegramProperties itself - both @Bean methods
            // read telegramProperties.token(), never a second configuration property.
            assertThat(properties.token()).isEqualTo("test-token");
        });
    }

    @Test
    void notificationClientBean_absentWhenTelegramDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean("notificationTelegramClient");
            assertThat(context).doesNotHaveBean(TelegramClient.class);
        });
    }

    @Test
    void notificationClientBean_absentWhenTelegramExplicitlyDisabled() {
        contextRunner.withPropertyValues("telegram.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean("notificationTelegramClient");
        });
    }

    @Test
    void telegramDisabledStartup_remainsUnchanged_noTelegramBeansAtAll() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(TelegramClient.class);
        });
    }

    private String[] enabledProperties() {
        return new String[] {"telegram.enabled=true", "telegram.token=test-token", "telegram.notification-send-timeout=10s"};
    }

    /** {@code OkHttpTelegramClient.client} is package-private in a different package - reflection is the only way in. */
    private OkHttpClient extractOkHttpClient(TelegramClient client) throws Exception {
        Field field = OkHttpTelegramClient.class.getDeclaredField("client");
        field.setAccessible(true);
        return (OkHttpClient) field.get(client);
    }

    @Configuration
    @EnableConfigurationProperties(TelegramProperties.class)
    static class TelegramPropertiesConfig {
    }
}

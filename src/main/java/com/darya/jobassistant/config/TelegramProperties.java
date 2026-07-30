package com.darya.jobassistant.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(boolean enabled, String token, Duration notificationSendTimeout) {

    private static final Duration MIN_NOTIFICATION_SEND_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration MAX_NOTIFICATION_SEND_TIMEOUT = Duration.ofSeconds(60);

    public TelegramProperties {
        if (enabled) {
            if (notificationSendTimeout == null
                    || notificationSendTimeout.compareTo(MIN_NOTIFICATION_SEND_TIMEOUT) < 0
                    || notificationSendTimeout.compareTo(MAX_NOTIFICATION_SEND_TIMEOUT) > 0) {
                throw new IllegalArgumentException(
                        "telegram.notification-send-timeout must be between " + MIN_NOTIFICATION_SEND_TIMEOUT
                                + " and " + MAX_NOTIFICATION_SEND_TIMEOUT + " when telegram.enabled=true, but was "
                                + notificationSendTimeout);
            }
        }
    }
}

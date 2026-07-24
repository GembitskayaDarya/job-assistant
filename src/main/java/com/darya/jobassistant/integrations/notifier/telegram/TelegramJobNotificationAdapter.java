package com.darya.jobassistant.integrations.notifier.telegram;

import com.darya.jobassistant.integrations.notifier.JobNotification;
import com.darya.jobassistant.integrations.notifier.JobNotificationException;
import com.darya.jobassistant.integrations.notifier.JobNotificationFailureType;
import com.darya.jobassistant.integrations.notifier.JobNotificationPort;
import com.darya.jobassistant.integrations.notifier.JobNotificationResult;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Telegram implementation of {@link JobNotificationPort}. Reuses the existing {@link
 * TelegramClient} bean (the same client the bot uses to send command replies) rather than a
 * second bot/session/token. Stateless: holds only its two collaborators.
 *
 * <p>Scope is intentionally narrow - format, send, translate the outcome. It never touches
 * vacancy/analysis/notification-delivery persistence and never decides whether to reserve or
 * mark a delivery; that orchestration belongs to the future JobMonitoringService.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "telegram", name = "enabled", havingValue = "true")
public class TelegramJobNotificationAdapter implements JobNotificationPort {

    private static final int FORBIDDEN = 403;
    private static final int BAD_REQUEST = 400;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int SERVER_ERROR_RANGE_START = 500;
    private static final int SERVER_ERROR_RANGE_END = 599;

    private final TelegramClient telegramClient;
    private final TelegramJobNotificationFormatter formatter;

    @Override
    public JobNotificationResult send(JobNotification notification) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(notification.recipientChatId())
                .text(formatter.format(notification))
                .parseMode(ParseMode.MARKDOWNV2)
                .build();
        try {
            return toResult(telegramClient.execute(sendMessage));
        } catch (TelegramApiException e) {
            throw translate(e);
        }
    }

    private JobNotificationResult toResult(Message sent) {
        if (sent == null || sent.getMessageId() == null) {
            return JobNotificationResult.accepted();
        }
        return JobNotificationResult.accepted(String.valueOf(sent.getMessageId()));
    }

    private JobNotificationException translate(TelegramApiException e) {
        if (e instanceof TelegramApiRequestException requestException) {
            return translateRequestException(requestException);
        }
        if (e.getCause() instanceof IOException) {
            return new JobNotificationException(
                    JobNotificationFailureType.TEMPORARY_FAILURE,
                    "Telegram notification was temporarily rejected", e);
        }
        return new JobNotificationException(
                JobNotificationFailureType.UNEXPECTED_FAILURE,
                "Unexpected Telegram notification failure", e);
    }

    private JobNotificationException translateRequestException(TelegramApiRequestException e) {
        Integer errorCode = e.getErrorCode();
        Integer retryAfter = e.getParameters() != null ? e.getParameters().getRetryAfter() : null;

        boolean isServerError = errorCode != null && errorCode >= SERVER_ERROR_RANGE_START && errorCode <= SERVER_ERROR_RANGE_END;
        if (retryAfter != null || (errorCode != null && errorCode == TOO_MANY_REQUESTS) || isServerError) {
            return new JobNotificationException(
                    JobNotificationFailureType.TEMPORARY_FAILURE,
                    "Telegram notification was temporarily rejected", e);
        }
        if (errorCode != null && (errorCode == FORBIDDEN || errorCode == BAD_REQUEST)) {
            return new JobNotificationException(
                    JobNotificationFailureType.PERMANENT_FAILURE,
                    "Telegram notification cannot be delivered to the recipient", e);
        }
        return new JobNotificationException(
                JobNotificationFailureType.UNEXPECTED_FAILURE,
                "Unexpected Telegram notification failure", e);
    }
}

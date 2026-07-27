package com.darya.jobassistant.integrations.notifier.telegram;

import com.darya.jobassistant.integrations.notifier.JobNotification;
import com.darya.jobassistant.integrations.notifier.JobNotificationException;
import com.darya.jobassistant.integrations.notifier.JobNotificationFailureType;
import com.darya.jobassistant.integrations.notifier.JobNotificationPort;
import com.darya.jobassistant.integrations.notifier.JobNotificationResult;
import com.darya.jobassistant.util.TelegramMessageUtils;
import java.io.IOException;
import java.util.List;
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
 * <p>Scope is intentionally narrow - format, split if needed, send, translate the outcome. It
 * never touches vacancy/analysis/notification-delivery persistence and never decides whether to
 * reserve or mark a delivery; that orchestration belongs to {@code JobMonitoringService}.
 *
 * <p>Message-length handling lives here, not in {@link TelegramJobNotificationFormatter}: the
 * formatter always returns the complete, unmodified text (including the shared analysis block
 * exactly as {@code JobAnalysisTelegramFormatter} produced it); this adapter splits that text via
 * {@link TelegramMessageUtils#split(String)} into as many Telegram messages as needed and sends
 * them in order. This keeps "how do we render an analysis" and "how do we fit it into Telegram's
 * transport limit" as separate concerns, and guarantees a long analysis is never rendered
 * differently from a short one. If a later chunk fails to send after earlier ones already
 * succeeded, the already-delivered chunks cannot be recalled - this is an accepted limitation of
 * multi-message delivery with no atomic "send all or nothing" primitive in the Telegram Bot API.
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
        List<String> chunks = TelegramMessageUtils.split(formatter.format(notification));
        JobNotificationResult result = JobNotificationResult.accepted();
        for (String chunk : chunks) {
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(notification.recipientChatId())
                    .text(chunk)
                    .parseMode(ParseMode.MARKDOWNV2)
                    .build();
            try {
                result = toResult(telegramClient.execute(sendMessage));
            } catch (TelegramApiException e) {
                throw translate(e);
            }
        }
        return result;
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

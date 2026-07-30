package com.darya.jobassistant.integrations.notifier.telegram;

import com.darya.jobassistant.integrations.notifier.CompactVacancyRecommendation;
import com.darya.jobassistant.integrations.notifier.JobNotification;
import com.darya.jobassistant.integrations.notifier.JobNotificationException;
import com.darya.jobassistant.integrations.notifier.JobNotificationFailureType;
import com.darya.jobassistant.integrations.notifier.JobNotificationPort;
import com.darya.jobassistant.integrations.notifier.JobNotificationResult;
import com.darya.jobassistant.util.TelegramMessageUtils;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Telegram implementation of {@link JobNotificationPort}. Uses a dedicated {@code
 * notificationTelegramClient} bean (see {@code TelegramBotConfig}) - separate from the bean the
 * bot's long-polling loop and command replies use - built with {@code
 * retryOnConnectionFailure(false)}: a single processing attempt (either {@link #send} or {@link
 * #sendCompactRecommendation}) must never contain a hidden OkHttp transport-level replay of a
 * message-send request. Both methods still reuse the same bot token; only the transport/retry
 * posture differs. Stateless otherwise: holds only its collaborators.
 *
 * <p>Scope is intentionally narrow - format, send, translate the outcome. It never touches
 * vacancy/analysis/notification-delivery persistence and never decides whether to reserve or mark
 * a delivery; that orchestration belongs to {@code JobMonitoringService}/{@code
 * VacancyRecommendationProcessingService}.
 *
 * <p>{@link #send} (legacy monitoring path): message-length handling lives here, not in {@link
 * TelegramJobNotificationFormatter} - the formatter always returns the complete, unmodified text
 * (including the shared analysis block exactly as {@code JobAnalysisTelegramFormatter} produced
 * it); this adapter splits that text via {@link TelegramMessageUtils#split(String)} into as many
 * Telegram messages as needed and sends them in order. If a later chunk fails to send after
 * earlier ones already succeeded, the already-delivered chunks cannot be recalled - this is an
 * accepted limitation of multi-message delivery with no atomic "send all or nothing" primitive in
 * the Telegram Bot API. This behavior is completely unchanged by {@link #sendCompactRecommendation}.
 *
 * <p>{@link #sendCompactRecommendation} (automatic recommendation path): never chunks. {@link
 * CompactRecommendationTelegramFormatter} guarantees at most one message; if it cannot fit even
 * essential fields, this method fails with {@link JobNotificationFailureType#PAYLOAD_TOO_LARGE}
 * before {@code telegramClient.execute} is ever called - zero Telegram HTTP requests for that
 * outcome.
 */
@Component
@ConditionalOnProperty(prefix = "telegram", name = "enabled", havingValue = "true")
public class TelegramJobNotificationAdapter implements JobNotificationPort {

    private static final int FORBIDDEN = 403;
    private static final int BAD_REQUEST = 400;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int SERVER_ERROR_RANGE_START = 500;
    private static final int SERVER_ERROR_RANGE_END = 599;

    private final TelegramClient telegramClient;
    private final TelegramJobNotificationFormatter formatter;
    private final CompactRecommendationTelegramFormatter compactFormatter;

    public TelegramJobNotificationAdapter(
            @Qualifier("notificationTelegramClient") TelegramClient telegramClient,
            TelegramJobNotificationFormatter formatter,
            CompactRecommendationTelegramFormatter compactFormatter) {
        this.telegramClient = telegramClient;
        this.formatter = formatter;
        this.compactFormatter = compactFormatter;
    }

    @Override
    public JobNotificationResult send(JobNotification notification) {
        List<String> chunks = TelegramMessageUtils.split(formatter.format(notification));
        JobNotificationResult result = JobNotificationResult.accepted();
        for (String chunk : chunks) {
            result = sendOneMessage(notification.recipientChatId(), chunk);
        }
        return result;
    }

    @Override
    public JobNotificationResult sendCompactRecommendation(CompactVacancyRecommendation recommendation) {
        Optional<String> rendered = compactFormatter.format(recommendation);
        if (rendered.isEmpty()) {
            throw new JobNotificationException(JobNotificationFailureType.PAYLOAD_TOO_LARGE,
                    "Recommendation message could not fit within Telegram's single-message limit");
        }
        return sendOneMessage(recommendation.recipientChatId(), rendered.get());
    }

    private JobNotificationResult sendOneMessage(Long recipientChatId, String text) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(recipientChatId)
                .text(text)
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

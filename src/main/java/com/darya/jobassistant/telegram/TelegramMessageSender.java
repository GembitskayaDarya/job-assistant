package com.darya.jobassistant.telegram;

import com.darya.jobassistant.telegram.command.BotResponse;
import com.darya.jobassistant.telegram.command.TelegramDocument;
import com.darya.jobassistant.telegram.command.TelegramDocumentDeliveryResult;
import com.darya.jobassistant.telegram.command.TelegramSendResult;
import com.darya.jobassistant.util.TelegramMessageUtils;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * The single place that turns a {@link BotResponse} (or a plain callback-answer text) into an
 * actual Telegram API call. Extracted out of {@link JobAssistantTelegramBot} so that other
 * Telegram adapter components - such as {@code VacancyAnalysisCallbackHandler}, which must
 * acknowledge a callback query and later send a new message itself, on its own schedule, rather
 * than through the bot's synchronous handle-then-respond flow - can reuse the same send/answer/edit
 * mechanics without duplicating them.
 *
 * <p>Conditional on {@code telegram.enabled=true}, matching the {@link TelegramClient} bean itself
 * ({@code TelegramBotConfig}) and {@link JobAssistantTelegramBot}: with Telegram disabled there is
 * no client for this to wrap.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "telegram", name = "enabled", havingValue = "true")
public class TelegramMessageSender {

    private final TelegramClient telegramClient;

    /**
     * Release-gate fix: returns a {@link TelegramSendResult} rather than {@code void} - a document
     * upload failure is never thrown (a single flaky document must never abort delivery of the
     * others, or of the text message that already succeeded), but it must be <em>observable</em>: a
     * caller that promised the user "your documents are ready" needs to know whether that was
     * actually true before treating the interaction as finished - see {@code
     * ApplicationPackageCallbackHandler}/{@code JobAssistantTelegramBot} for how that observation is
     * used. Every pre-existing caller that never inspected the old {@code void} return value keeps
     * compiling and behaving identically - ignoring a return value requires no code change.
     */
    public TelegramSendResult send(Long chatId, BotResponse response) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(TelegramMessageUtils.truncate(response.text()))
                .parseMode(response.parseMode())
                .replyMarkup(response.keyboard())
                .build();
        boolean textDelivered = true;
        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram message to chat {}", chatId, e);
            textDelivered = false;
        }
        List<TelegramDocumentDeliveryResult> documentResults = new ArrayList<>();
        for (TelegramDocument document : response.documents()) {
            documentResults.add(sendDocument(chatId, document));
        }
        return new TelegramSendResult(textDelivered, documentResults);
    }

    /**
     * Uploads {@code document}'s bytes directly via the Telegram Bot API's multipart upload
     * mechanism ({@link InputFile}'s stream constructor) - never a local filesystem path. A failure
     * is logged here (full exception, for debugging) and reported back as a non-delivered {@link
     * TelegramDocumentDeliveryResult} - never thrown - so {@link #send} always attempts every
     * remaining document in {@code response.documents()} regardless of an earlier one's outcome.
     */
    private TelegramDocumentDeliveryResult sendDocument(Long chatId, TelegramDocument document) {
        SendDocument sendDocument = SendDocument.builder()
                .chatId(chatId)
                .document(new InputFile(new ByteArrayInputStream(document.content()), document.fileName()))
                .build();
        try {
            telegramClient.execute(sendDocument);
            log.info("Sent document '{}' to chat {}", document.fileName(), chatId);
            return new TelegramDocumentDeliveryResult(document.fileName(), true);
        } catch (TelegramApiException e) {
            log.error("Failed to send document '{}' to chat {}", document.fileName(), chatId, e);
            return new TelegramDocumentDeliveryResult(document.fileName(), false);
        }
    }

    public void answerCallbackQuery(String callbackQueryId, String answerText) {
        AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQueryId)
                .text(answerText)
                .build();
        try {
            telegramClient.execute(answer);
        } catch (TelegramApiException e) {
            log.error("Failed to answer callback query {}", callbackQueryId, e);
        }
    }

    public void editMessage(Long chatId, Integer messageId, BotResponse response) {
        EditMessageText editMessageText = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(TelegramMessageUtils.truncate(response.text()))
                .parseMode(response.parseMode())
                .replyMarkup(response.keyboard())
                .build();
        try {
            telegramClient.execute(editMessageText);
        } catch (TelegramApiException e) {
            log.error("Failed to edit message {} in chat {}", messageId, chatId, e);
        }
    }
}

package com.darya.jobassistant.telegram;

import com.darya.jobassistant.telegram.command.BotResponse;
import com.darya.jobassistant.util.TelegramMessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
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

    public void send(Long chatId, BotResponse response) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(TelegramMessageUtils.truncate(response.text()))
                .parseMode(response.parseMode())
                .replyMarkup(response.keyboard())
                .build();
        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram message to chat {}", chatId, e);
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

package com.darya.jobassistant.telegram;

import com.darya.jobassistant.config.TelegramProperties;
import com.darya.jobassistant.telegram.callback.VacancyImportCallbackHandler;
import com.darya.jobassistant.telegram.callback.VacancyImportCallbackOutcome;
import com.darya.jobassistant.telegram.command.BotResponse;
import com.darya.jobassistant.telegram.command.CommandRegistry;
import com.darya.jobassistant.util.TelegramMessageUtils;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.DefaultLongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "telegram", name = "enabled", havingValue = "true")
public class JobAssistantTelegramBot extends DefaultLongPollingUpdateConsumer implements SpringLongPollingBot {

    private final TelegramProperties telegramProperties;
    private final TelegramClient telegramClient;
    private final CommandRegistry commandRegistry;
    private final VacancyImportMessageHandler vacancyImportMessageHandler;
    private final VacancyImportCallbackHandler vacancyImportCallbackHandler;

    @Override
    public String getBotToken() {
        return telegramProperties.token();
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
            return;
        }
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        Message message = update.getMessage();
        sendMessage(message.getChatId(), handleMessage(message));
    }

    @AfterBotRegistration
    public void registerCommandsWithTelegram() {
        try {
            telegramClient.execute(new SetMyCommands(commandRegistry.toBotCommands(), new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Failed to register commands with Telegram", e);
        }
    }

    private BotResponse handleMessage(Message message) {
        String text = message.getText().trim();
        if (!text.startsWith("/")) {
            return vacancyImportMessageHandler.handle(message).orElseGet(() -> BotResponse.text("Echo: " + text));
        }
        String commandName = text.split("\\s+", 2)[0];
        return commandRegistry.find(commandName)
                .map(command -> command.execute(message))
                .orElseGet(() -> BotResponse.text("Sorry, I didn't understand that command. Send /help for a list of commands."));
    }

    /**
     * Dispatches only to {@link VacancyImportCallbackHandler}. An {@link Optional#empty()} result
     * means the callback data wasn't recognized as ours - nothing else currently consumes callback
     * queries, so it is left un-acknowledged exactly as it always was before this handler existed,
     * ready for a future handler to claim.
     */
    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        Optional<VacancyImportCallbackOutcome> outcome = vacancyImportCallbackHandler.handle(callbackQuery);
        if (outcome.isEmpty()) {
            return;
        }
        answerCallbackQuery(callbackQuery.getId(), outcome.get().answerText());
        if (outcome.get().editedMessage() != null) {
            editMessage(callbackQuery.getMessage().getChatId(), callbackQuery.getMessage().getMessageId(), outcome.get().editedMessage());
        }
    }

    private void sendMessage(Long chatId, BotResponse response) {
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

    private void answerCallbackQuery(String callbackQueryId, String answerText) {
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

    private void editMessage(Long chatId, Integer messageId, BotResponse response) {
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

package com.darya.jobassistant.telegram;

import com.darya.jobassistant.config.TelegramProperties;
import com.darya.jobassistant.telegram.callback.ApplicationPackageCallbackHandler;
import com.darya.jobassistant.telegram.callback.VacancyAnalysisCallbackHandler;
import com.darya.jobassistant.telegram.callback.VacancyImportCallbackHandler;
import com.darya.jobassistant.telegram.callback.VacancyImportCallbackOutcome;
import com.darya.jobassistant.telegram.command.BotResponse;
import com.darya.jobassistant.telegram.command.CommandRegistry;
import com.darya.jobassistant.telegram.command.TelegramSendResult;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.DefaultLongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
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
    private final TelegramMessageSender telegramMessageSender;
    private final CommandRegistry commandRegistry;
    private final VacancyImportMessageHandler vacancyImportMessageHandler;
    private final VacancyImportCallbackHandler vacancyImportCallbackHandler;
    private final VacancyAnalysisCallbackHandler vacancyAnalysisCallbackHandler;
    private final ApplicationPackageCallbackHandler applicationPackageCallbackHandler;

    private static final String DOCUMENT_DELIVERY_FAILURE_MESSAGE = """
            I generated your documents but couldn't deliver them here.

            Please try again.""";

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
        TelegramSendResult sendResult = telegramMessageSender.send(message.getChatId(), handleMessage(message));
        // Release-gate fix: a generic, safe safety net - covers /prepare (and any future command
        // whose BotResponse carries documents) without coupling this generic dispatcher to
        // applicationmaterials-specific failure-reason types. Never fires for a plain text-only
        // response (BotResponse.documents() is empty, so allDocumentsDelivered() is vacuously true).
        if (!sendResult.allDocumentsDelivered()) {
            log.error("Failed to deliver one or more documents to chat {}", message.getChatId());
            telegramMessageSender.send(message.getChatId(), BotResponse.text(DOCUMENT_DELIVERY_FAILURE_MESSAGE));
        }
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
     * Explicit, ordered routing: try {@link VacancyAnalysisCallbackHandler} (the {@code
     * via:analyze:} prefix), then {@link ApplicationPackageCallbackHandler} (the {@code am:prepare:}
     * prefix) - both fully handle their own acknowledgement and response, so a {@code true} result
     * means nothing more to do here. Otherwise fall through to {@link VacancyImportCallbackHandler}
     * (the {@code vi:} prefix), whose {@link Optional#empty()} result means the callback data
     * wasn't recognized by any of them - nothing else currently consumes callback queries, so it is
     * left un-acknowledged exactly as it always was before any handler existed, ready for a future
     * handler to claim.
     */
    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        if (vacancyAnalysisCallbackHandler.handle(callbackQuery)) {
            return;
        }
        if (applicationPackageCallbackHandler.handle(callbackQuery)) {
            return;
        }
        Optional<VacancyImportCallbackOutcome> outcome = vacancyImportCallbackHandler.handle(callbackQuery);
        if (outcome.isEmpty()) {
            return;
        }
        telegramMessageSender.answerCallbackQuery(callbackQuery.getId(), outcome.get().answerText());
        if (outcome.get().editedMessage() != null) {
            telegramMessageSender.editMessage(
                    callbackQuery.getMessage().getChatId(), callbackQuery.getMessage().getMessageId(), outcome.get().editedMessage());
        }
    }
}

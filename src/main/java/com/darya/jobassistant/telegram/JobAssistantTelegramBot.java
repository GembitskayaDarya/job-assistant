package com.darya.jobassistant.telegram;

import com.darya.jobassistant.config.TelegramProperties;
import com.darya.jobassistant.telegram.command.BotResponse;
import com.darya.jobassistant.telegram.command.CommandRegistry;
import com.darya.jobassistant.util.TelegramMessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.DefaultLongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
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
            return BotResponse.text("Echo: " + text);
        }
        String commandName = text.split("\\s+", 2)[0];
        return commandRegistry.find(commandName)
                .map(command -> command.execute(message))
                .orElseGet(() -> BotResponse.text("Sorry, I didn't understand that command. Send /help for a list of commands."));
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
}

package com.darya.jobassistant.telegram;

import com.darya.jobassistant.telegram.command.CommandRegistry;
import com.darya.jobassistant.util.TelegramMessageUtils;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
public class JobAssistantTelegramBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final CommandRegistry commandRegistry;

    public JobAssistantTelegramBot(String botToken, String botUsername, CommandRegistry commandRegistry) {
        super(botToken);
        this.botUsername = botUsername;
        this.commandRegistry = commandRegistry;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        Message message = update.getMessage();
        sendMessage(message.getChatId(), handleMessage(message));
    }

    public void registerCommandsWithTelegram() {
        try {
            execute(new SetMyCommands(commandRegistry.toBotCommands(), new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Failed to register commands with Telegram", e);
        }
    }

    private String handleMessage(Message message) {
        String text = message.getText().trim();
        if (!text.startsWith("/")) {
            return "Echo: " + text;
        }
        String commandName = text.split("\\s+", 2)[0];
        return commandRegistry.find(commandName)
                .map(command -> command.execute(message))
                .orElse("Sorry, I didn't understand that command. Send /help for a list of commands.");
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(TelegramMessageUtils.truncate(text))
                .build();
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram message to chat {}", chatId, e);
        }
    }
}

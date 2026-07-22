package com.darya.jobassistant.telegram.command;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Component
public class StartCommand implements TelegramCommand {

    @Override
    public String name() {
        return "/start";
    }

    @Override
    public String description() {
        return "Start using the bot";
    }

    @Override
    public String execute(Message message) {
        return "Welcome to Job Assistant Bot! Send /help to see available commands.";
    }
}

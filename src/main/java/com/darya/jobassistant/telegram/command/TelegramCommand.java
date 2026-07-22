package com.darya.jobassistant.telegram.command;

import org.telegram.telegrambots.meta.api.objects.message.Message;

public interface TelegramCommand {

    String name();

    String description();

    BotResponse execute(Message message);
}

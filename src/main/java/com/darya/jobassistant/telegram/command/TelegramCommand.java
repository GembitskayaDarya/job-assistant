package com.darya.jobassistant.telegram.command;

import org.telegram.telegrambots.meta.api.objects.Message;

public interface TelegramCommand {

    String name();

    String description();

    String execute(Message message);
}

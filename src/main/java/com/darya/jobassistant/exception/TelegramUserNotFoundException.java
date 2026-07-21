package com.darya.jobassistant.exception;

import java.util.UUID;

public class TelegramUserNotFoundException extends NotFoundException {

    public TelegramUserNotFoundException(UUID id) {
        super("Telegram user not found with id: " + id);
    }
}

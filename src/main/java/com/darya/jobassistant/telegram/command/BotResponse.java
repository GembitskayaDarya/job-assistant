package com.darya.jobassistant.telegram.command;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

public record BotResponse(String text, String parseMode, InlineKeyboardMarkup keyboard) {

    public static BotResponse text(String text) {
        return new BotResponse(text, null, null);
    }
}

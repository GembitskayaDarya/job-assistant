package com.darya.jobassistant.telegram.command;

import java.util.List;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

public record BotResponse(String text, String parseMode, InlineKeyboardMarkup keyboard, List<TelegramDocument> documents) {

    public BotResponse {
        documents = documents == null ? List.of() : List.copyOf(documents);
    }

    /**
     * Preserves every existing three-argument call site (no documents attached) - the vast
     * majority of {@link BotResponse}s are plain text/keyboard replies with nothing to upload.
     */
    public BotResponse(String text, String parseMode, InlineKeyboardMarkup keyboard) {
        this(text, parseMode, keyboard, List.of());
    }

    public static BotResponse text(String text) {
        return new BotResponse(text, null, null, List.of());
    }
}

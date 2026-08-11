package com.darya.jobassistant.telegram.command;

/**
 * Sprint 10 Step 5: one document to upload alongside a {@link BotResponse} - the Telegram-adapter
 * counterpart of {@code applicationmaterials.preparation.PreparedDocument}, built by a formatter
 * from that provider-neutral type. Carries only what {@code TelegramMessageSender} needs to build a
 * Telegram {@code InputFile}: a user-visible file name and the raw bytes.
 */
public record TelegramDocument(String fileName, byte[] content) {

    public TelegramDocument {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Telegram document fileName must not be blank");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Telegram document content must not be empty");
        }
    }
}

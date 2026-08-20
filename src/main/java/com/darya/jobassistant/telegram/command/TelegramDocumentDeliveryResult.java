package com.darya.jobassistant.telegram.command;

/** Release-gate fix: one {@link TelegramDocument}'s individual delivery outcome within a {@link TelegramSendResult}. */
public record TelegramDocumentDeliveryResult(String fileName, boolean delivered) {

    public TelegramDocumentDeliveryResult {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Telegram document delivery result fileName must not be blank");
        }
    }
}

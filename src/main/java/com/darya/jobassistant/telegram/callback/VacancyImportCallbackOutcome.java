package com.darya.jobassistant.telegram.callback;

import com.darya.jobassistant.telegram.command.BotResponse;

/**
 * What {@link VacancyImportCallbackHandler} wants done with a processed callback query.
 * {@code editedMessage} reuses {@link BotResponse}'s text/parseMode/keyboard shape - the same
 * fields {@code EditMessageText} needs - rather than introducing a parallel type, since editing a
 * message and sending one require identical content. {@code answerText} is shown as a Telegram
 * callback-answer toast; it may be null (silent acknowledgement) whether or not the message is
 * also edited.
 */
public record VacancyImportCallbackOutcome(BotResponse editedMessage, String answerText) {

    public static VacancyImportCallbackOutcome edit(BotResponse editedMessage) {
        return new VacancyImportCallbackOutcome(editedMessage, null);
    }

    public static VacancyImportCallbackOutcome toast(String answerText) {
        return new VacancyImportCallbackOutcome(null, answerText);
    }
}

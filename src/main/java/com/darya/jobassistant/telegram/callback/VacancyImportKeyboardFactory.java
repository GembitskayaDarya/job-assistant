package com.darya.jobassistant.telegram.callback;

import com.darya.jobassistant.vacancyimport.model.VacancyImportAction;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

/**
 * Builds the inline keyboards attached to vacancy-import Telegram messages. The only place that
 * constructs {@link InlineKeyboardMarkup} for this workflow, and always through {@link
 * VacancyImportCallbackData#format()}/{@link VacancyAnalysisCallbackData#format()} - no callback
 * string is ever built ad hoc elsewhere.
 */
@Component
public class VacancyImportKeyboardFactory {

    /** Empty keyboard payload Telegram interprets as "remove the reply markup entirely". */
    public static final InlineKeyboardMarkup NO_BUTTONS = InlineKeyboardMarkup.builder().build();

    public InlineKeyboardMarkup confirmationKeyboard(UUID sessionId) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        button("✅ Save", VacancyImportAction.SAVE, sessionId),
                        button("🔄 Try again", VacancyImportAction.RETRY, sessionId),
                        button("❌ Cancel", VacancyImportAction.CANCEL, sessionId)))
                .build();
    }

    public InlineKeyboardMarkup openVacancyKeyboard(String url) {
        if (url == null || url.isBlank()) {
            return NO_BUTTONS;
        }
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(InlineKeyboardButton.builder().text("Open vacancy").url(url).build()))
                .build();
    }

    /**
     * The keyboard shown after Save - whether the Vacancy was newly created, reused, or this is a
     * repeated (idempotent) Save - so the same keyboard always follows a {@code Saved} outcome.
     * The URL button is present only when {@code url} is available; the Analyze button, using the
     * import-session id (never the Vacancy id - see {@link VacancyAnalysisCallbackData}), is
     * always present.
     */
    public InlineKeyboardMarkup savedVacancyKeyboard(String url, UUID importSessionId) {
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        if (url != null && !url.isBlank()) {
            buttons.add(InlineKeyboardButton.builder().text("🔗 Open vacancy").url(url).build());
        }
        buttons.add(InlineKeyboardButton.builder()
                .text("🤖 Analyze")
                .callbackData(new VacancyAnalysisCallbackData(importSessionId).format())
                .build());
        return InlineKeyboardMarkup.builder().keyboardRow(new InlineKeyboardRow(buttons)).build();
    }

    private InlineKeyboardButton button(String label, VacancyImportAction action, UUID sessionId) {
        return InlineKeyboardButton.builder()
                .text(label)
                .callbackData(new VacancyImportCallbackData(action, sessionId).format())
                .build();
    }
}

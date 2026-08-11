package com.darya.jobassistant.telegram.callback;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

/**
 * Sprint 10 Step 5: builds the single "Prepare CV &amp; Cover Letter" inline button attached to a
 * vacancy-analysis response, always through {@link ApplicationPackageCallbackData#format()} - no
 * callback string is built ad hoc elsewhere, matching {@code VacancyImportKeyboardFactory}'s
 * convention for the import workflow's own keyboards.
 */
@Component
public class ApplicationPackageKeyboardFactory {

    private static final String BUTTON_LABEL = "📄 Prepare CV & Cover Letter";

    public InlineKeyboardMarkup prepareApplicationPackageKeyboard(UUID vacancyId) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(InlineKeyboardButton.builder()
                        .text(BUTTON_LABEL)
                        .callbackData(new ApplicationPackageCallbackData(vacancyId).format())
                        .build()))
                .build();
    }
}

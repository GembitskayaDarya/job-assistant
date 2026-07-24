package com.darya.jobassistant.telegram.callback;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.vacancyimport.model.VacancyImportAction;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

class VacancyImportKeyboardFactoryTest {

    private final VacancyImportKeyboardFactory factory = new VacancyImportKeyboardFactory();

    @Test
    void confirmationKeyboard_containsSaveRetryAndCancelButtonsWithCorrectCallbackData() {
        UUID sessionId = UUID.randomUUID();

        InlineKeyboardMarkup keyboard = factory.confirmationKeyboard(sessionId);

        List<InlineKeyboardButton> buttons = onlyRow(keyboard);
        assertThat(buttons).hasSize(3);
        assertThat(buttons.get(0).getText()).contains("Save");
        assertThat(buttons.get(0).getCallbackData())
                .isEqualTo(new VacancyImportCallbackData(VacancyImportAction.SAVE, sessionId).format());
        assertThat(buttons.get(1).getText()).contains("Try again");
        assertThat(buttons.get(1).getCallbackData())
                .isEqualTo(new VacancyImportCallbackData(VacancyImportAction.RETRY, sessionId).format());
        assertThat(buttons.get(2).getText()).contains("Cancel");
        assertThat(buttons.get(2).getCallbackData())
                .isEqualTo(new VacancyImportCallbackData(VacancyImportAction.CANCEL, sessionId).format());
    }

    @Test
    void confirmationKeyboard_differentSessions_produceDifferentCallbackData() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        String firstSave = onlyRow(factory.confirmationKeyboard(first)).get(0).getCallbackData();
        String secondSave = onlyRow(factory.confirmationKeyboard(second)).get(0).getCallbackData();

        assertThat(firstSave).isNotEqualTo(secondSave);
    }

    @Test
    void openVacancyKeyboard_validUrl_producesUrlButton() {
        InlineKeyboardMarkup keyboard = factory.openVacancyKeyboard("https://example.com/job/123");

        List<InlineKeyboardButton> buttons = onlyRow(keyboard);
        assertThat(buttons).hasSize(1);
        assertThat(buttons.get(0).getText()).contains("Open vacancy");
        assertThat(buttons.get(0).getUrl()).isEqualTo("https://example.com/job/123");
        assertThat(buttons.get(0).getCallbackData()).isNull();
    }

    @Test
    void openVacancyKeyboard_noUrl_producesNoButtons() {
        InlineKeyboardMarkup keyboard = factory.openVacancyKeyboard(null);

        assertThat(keyboard.getKeyboard()).isEmpty();
    }

    private List<InlineKeyboardButton> onlyRow(InlineKeyboardMarkup keyboard) {
        assertThat(keyboard.getKeyboard()).hasSize(1);
        InlineKeyboardRow row = keyboard.getKeyboard().get(0);
        return row;
    }
}

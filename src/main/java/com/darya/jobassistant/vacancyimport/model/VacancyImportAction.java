package com.darya.jobassistant.vacancyimport.model;

/**
 * A provider-neutral confirmation-step decision, triggered from Telegram inline buttons but
 * carrying no Telegram-specific meaning itself.
 */
public enum VacancyImportAction {
    SAVE,
    RETRY,
    CANCEL
}

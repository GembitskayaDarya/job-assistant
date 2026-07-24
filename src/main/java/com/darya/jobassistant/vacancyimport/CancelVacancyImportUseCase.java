package com.darya.jobassistant.vacancyimport;

import com.darya.jobassistant.vacancyimport.dto.CancelVacancyImportResult;

public interface CancelVacancyImportUseCase {

    CancelVacancyImportResult cancel(long telegramChatId, long telegramUserId);
}

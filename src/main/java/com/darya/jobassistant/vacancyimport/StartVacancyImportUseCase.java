package com.darya.jobassistant.vacancyimport;

import com.darya.jobassistant.vacancyimport.dto.StartVacancyImportResult;

public interface StartVacancyImportUseCase {

    StartVacancyImportResult start(long telegramChatId, long telegramUserId);
}

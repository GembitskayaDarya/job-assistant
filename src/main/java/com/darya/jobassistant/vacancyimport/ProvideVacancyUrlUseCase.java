package com.darya.jobassistant.vacancyimport;

import com.darya.jobassistant.vacancyimport.dto.ProvideVacancyUrlResult;

public interface ProvideVacancyUrlUseCase {

    ProvideVacancyUrlResult provideUrl(long telegramChatId, long telegramUserId, String rawUrl);
}

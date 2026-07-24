package com.darya.jobassistant.vacancyimport;

import com.darya.jobassistant.vacancyimport.dto.ContinueVacancyImportResult;

/**
 * Routes an ordinary (non-command) Telegram text message according to the sender's current
 * import-session state. That routing decision - is this text a URL attempt, a description, or
 * something sent at a point where neither applies - belongs here, not in the Telegram adapter,
 * because it depends entirely on session state the adapter must not inspect.
 */
public interface ContinueVacancyImportUseCase {

    ContinueVacancyImportResult handleText(long telegramChatId, long telegramUserId, String text);
}

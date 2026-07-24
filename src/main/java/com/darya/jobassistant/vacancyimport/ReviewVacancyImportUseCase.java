package com.darya.jobassistant.vacancyimport;

import com.darya.jobassistant.vacancyimport.dto.ReviewVacancyImportResult;
import com.darya.jobassistant.vacancyimport.model.VacancyImportAction;
import java.util.UUID;

/**
 * Applies a confirmation-step decision (Save, Retry, or Cancel) to a recognized import session.
 * {@code telegramChatId}/{@code telegramUserId} are the trusted identity of the caller - taken
 * from the Telegram update itself, never from client-controlled callback data - and are checked
 * against the session's own owning chat/user before anything else happens.
 */
public interface ReviewVacancyImportUseCase {

    ReviewVacancyImportResult review(UUID sessionId, long telegramChatId, long telegramUserId, VacancyImportAction action);
}

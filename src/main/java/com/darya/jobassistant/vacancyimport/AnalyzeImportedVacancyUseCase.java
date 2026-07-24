package com.darya.jobassistant.vacancyimport;

import com.darya.jobassistant.vacancyimport.dto.AnalyzeImportedVacancyResult;
import java.util.UUID;

/**
 * Authorizes and delegates an "Analyze" request for a vacancy that came from the guided import
 * workflow. {@code telegramChatId}/{@code telegramUserId} are the trusted identity of the caller -
 * taken from the Telegram update itself, never from client-controlled callback data - checked
 * against the import session's own owning chat/user before anything else happens. The Vacancy to
 * analyze is always the one linked to that session server-side, never a Vacancy ID supplied by the
 * caller.
 */
public interface AnalyzeImportedVacancyUseCase {

    AnalyzeImportedVacancyResult analyze(UUID importSessionId, long telegramChatId, long telegramUserId);
}

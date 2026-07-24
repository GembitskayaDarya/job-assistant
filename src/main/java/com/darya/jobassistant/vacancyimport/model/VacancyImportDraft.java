package com.darya.jobassistant.vacancyimport.model;

import com.darya.jobassistant.vacancyextraction.model.ExtractedVacancyData;
import java.time.Instant;
import java.util.UUID;

/**
 * The AI-extracted data for one {@link VacancyImportSession}, awaiting user confirmation. Plain
 * value type (no state machine of its own, unlike {@link VacancyImportSession}) - at most one
 * exists per session, enforced by a unique constraint on {@code session_id}, so it is always
 * either absent or the authoritative draft for its session.
 */
public record VacancyImportDraft(
        UUID id,
        UUID sessionId,
        ExtractedVacancyData data,
        Instant createdAt,
        Instant updatedAt
) {
    public VacancyImportDraft {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt must not be null");
        }
    }
}

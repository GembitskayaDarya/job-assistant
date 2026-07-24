package com.darya.jobassistant.ai.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A {@link JobAnalysis} as durably stored for a specific vacancy. {@link JobAnalysis} itself
 * stays a plain AI-result value (it is also used for the ephemeral, never-persisted result of
 * the {@code /analyze} command) - {@code vacancyId} lives here rather than on {@link JobAnalysis}
 * so that only persistence-facing code needs to know a vacancy is involved.
 */
public record PersistedJobAnalysis(
        UUID id,
        UUID vacancyId,
        JobAnalysis analysis,
        Instant createdAt
) {
    public PersistedJobAnalysis {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (vacancyId == null) {
            throw new IllegalArgumentException("vacancyId must not be null");
        }
        if (analysis == null) {
            throw new IllegalArgumentException("analysis must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
    }
}

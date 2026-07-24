package com.darya.jobassistant.ai.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersistedJobAnalysisTest {

    private final UUID vacancyId = UUID.randomUUID();
    private final JobAnalysis analysis = new JobAnalysis(80, List.of("Java"), List.of(), List.of(), "Good match");

    @Test
    void validInstance_isCreated() {
        PersistedJobAnalysis persisted = new PersistedJobAnalysis(UUID.randomUUID(), vacancyId, analysis, Instant.now());

        assertThat(persisted.vacancyId()).isEqualTo(vacancyId);
        assertThat(persisted.analysis()).isSameAs(analysis);
    }

    @Test
    void nullId_isRejected() {
        assertThatThrownBy(() -> new PersistedJobAnalysis(null, vacancyId, analysis, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullVacancyId_isRejected() {
        assertThatThrownBy(() -> new PersistedJobAnalysis(UUID.randomUUID(), null, analysis, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullAnalysis_isRejected() {
        assertThatThrownBy(() -> new PersistedJobAnalysis(UUID.randomUUID(), vacancyId, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullCreatedAt_isRejected() {
        assertThatThrownBy(() -> new PersistedJobAnalysis(UUID.randomUUID(), vacancyId, analysis, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

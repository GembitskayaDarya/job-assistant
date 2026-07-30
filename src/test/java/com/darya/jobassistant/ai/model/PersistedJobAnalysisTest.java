package com.darya.jobassistant.ai.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersistedJobAnalysisTest {

    private final UUID vacancyId = UUID.randomUUID();
    private final JobAnalysis analysis = new JobAnalysis(
            80, List.of("Java"), List.of(), List.of(), List.of(),
            "6 years vs. no stated requirement.", "Remote preference matches.", "Good match");

    @Test
    void validInstance_isCreated() {
        PersistedJobAnalysis persisted = new PersistedJobAnalysis(
                UUID.randomUUID(), vacancyId, analysis, JobAnalysisModelVersion.CURRENT, AnalysisOrigin.MANUAL,
                Instant.now(), null);

        assertThat(persisted.vacancyId()).isEqualTo(vacancyId);
        assertThat(persisted.analysis()).isSameAs(analysis);
        assertThat(persisted.analysisVersion()).isEqualTo(JobAnalysisModelVersion.CURRENT);
        assertThat(persisted.analysisOrigin()).isEqualTo(AnalysisOrigin.MANUAL);
        assertThat(persisted.manuallyReviewedAt()).isNull();
    }

    @Test
    void nullId_isRejected() {
        assertThatThrownBy(() -> new PersistedJobAnalysis(
                null, vacancyId, analysis, JobAnalysisModelVersion.CURRENT, AnalysisOrigin.MANUAL, Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullVacancyId_isRejected() {
        assertThatThrownBy(() -> new PersistedJobAnalysis(
                UUID.randomUUID(), null, analysis, JobAnalysisModelVersion.CURRENT, AnalysisOrigin.MANUAL, Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullAnalysis_isRejected() {
        assertThatThrownBy(() -> new PersistedJobAnalysis(
                UUID.randomUUID(), vacancyId, null, JobAnalysisModelVersion.CURRENT, AnalysisOrigin.MANUAL, Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroAnalysisVersion_isRejected() {
        assertThatThrownBy(() -> new PersistedJobAnalysis(
                UUID.randomUUID(), vacancyId, analysis, 0, AnalysisOrigin.MANUAL, Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeAnalysisVersion_isRejected() {
        assertThatThrownBy(() -> new PersistedJobAnalysis(
                UUID.randomUUID(), vacancyId, analysis, -1, AnalysisOrigin.MANUAL, Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullAnalysisOrigin_isRejected() {
        assertThatThrownBy(() -> new PersistedJobAnalysis(
                UUID.randomUUID(), vacancyId, analysis, JobAnalysisModelVersion.CURRENT, null, Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullCreatedAt_isRejected() {
        assertThatThrownBy(() -> new PersistedJobAnalysis(
                UUID.randomUUID(), vacancyId, analysis, JobAnalysisModelVersion.CURRENT, AnalysisOrigin.MANUAL, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isManuallyReviewed_manualOrigin_isTrueEvenWithoutManuallyReviewedAt() {
        PersistedJobAnalysis persisted = new PersistedJobAnalysis(
                UUID.randomUUID(), vacancyId, analysis, JobAnalysisModelVersion.CURRENT, AnalysisOrigin.MANUAL,
                Instant.now(), null);

        assertThat(persisted.isManuallyReviewed()).isTrue();
    }

    @Test
    void isManuallyReviewed_automaticDiscoveryOriginWithManuallyReviewedAtSet_isTrue() {
        PersistedJobAnalysis persisted = new PersistedJobAnalysis(
                UUID.randomUUID(), vacancyId, analysis, JobAnalysisModelVersion.CURRENT, AnalysisOrigin.AUTOMATIC_DISCOVERY,
                Instant.now(), Instant.now());

        assertThat(persisted.isManuallyReviewed()).isTrue();
    }

    @Test
    void isManuallyReviewed_automaticDiscoveryOriginWithoutManuallyReviewedAt_isFalse() {
        PersistedJobAnalysis persisted = new PersistedJobAnalysis(
                UUID.randomUUID(), vacancyId, analysis, JobAnalysisModelVersion.CURRENT, AnalysisOrigin.AUTOMATIC_DISCOVERY,
                Instant.now(), null);

        assertThat(persisted.isManuallyReviewed()).isFalse();
    }

    @Test
    void isManuallyReviewed_legacyOriginWithoutManuallyReviewedAt_isFalse() {
        PersistedJobAnalysis persisted = new PersistedJobAnalysis(
                UUID.randomUUID(), vacancyId, analysis, JobAnalysisModelVersion.CURRENT, AnalysisOrigin.LEGACY,
                Instant.now(), null);

        assertThat(persisted.isManuallyReviewed()).isFalse();
    }
}

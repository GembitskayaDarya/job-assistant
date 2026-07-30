package com.darya.jobassistant.notifications.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.ai.model.AnalysisOrigin;
import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.ai.model.JobAnalysisModelVersion;
import com.darya.jobassistant.ai.model.PersistedJobAnalysis;
import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobNotificationCandidateTest {

    @Test
    void validCandidate_isCreated() {
        Vacancy vacancy = vacancy(UUID.randomUUID());
        PersistedJobAnalysis analysis = analysisFor(vacancy.getId());

        JobNotificationCandidate candidate = new JobNotificationCandidate(vacancy, analysis);

        assertThat(candidate.vacancy()).isSameAs(vacancy);
        assertThat(candidate.analysis()).isSameAs(analysis);
    }

    @Test
    void nullVacancy_isRejected() {
        PersistedJobAnalysis analysis = analysisFor(UUID.randomUUID());

        assertThatThrownBy(() -> new JobNotificationCandidate(null, analysis))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullAnalysis_isRejected() {
        Vacancy vacancy = vacancy(UUID.randomUUID());

        assertThatThrownBy(() -> new JobNotificationCandidate(vacancy, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void vacancyWithoutDurableId_isRejected() {
        Vacancy vacancy = vacancy(null);
        PersistedJobAnalysis analysis = analysisFor(UUID.randomUUID());

        assertThatThrownBy(() -> new JobNotificationCandidate(vacancy, analysis))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void analysisForDifferentVacancy_isRejected() {
        Vacancy vacancy = vacancy(UUID.randomUUID());
        PersistedJobAnalysis analysisForAnotherVacancy = analysisFor(UUID.randomUUID());

        assertThatThrownBy(() -> new JobNotificationCandidate(vacancy, analysisForAnotherVacancy))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exposedModels_areApplicationDomainObjectsNotJpaProjectionsOrProviderTypes() {
        Vacancy vacancy = vacancy(UUID.randomUUID());
        PersistedJobAnalysis analysis = analysisFor(vacancy.getId());

        JobNotificationCandidate candidate = new JobNotificationCandidate(vacancy, analysis);

        assertThat(candidate.vacancy()).isInstanceOf(Vacancy.class);
        assertThat(candidate.analysis()).isInstanceOf(PersistedJobAnalysis.class);
        assertThat(candidate.analysis().analysis()).isInstanceOf(JobAnalysis.class);
    }

    private Vacancy vacancy(UUID id) {
        Company company = Company.builder().name("Acme").build();
        Vacancy vacancy = Vacancy.builder()
                .company(company)
                .title("Backend Engineer")
                .url("https://example.com/job-1")
                .source("remoteok")
                .build();
        vacancy.setId(id);
        return vacancy;
    }

    private PersistedJobAnalysis analysisFor(UUID vacancyId) {
        JobAnalysis analysis = new JobAnalysis(
                85, List.of("Java"), List.of(), List.of(), List.of(),
                "6 years vs. no stated requirement.", "Remote preference matches.", "Strong match");
        return new PersistedJobAnalysis(
                UUID.randomUUID(), vacancyId, analysis, JobAnalysisModelVersion.CURRENT, AnalysisOrigin.MONITORING,
                Instant.now(), null);
    }
}

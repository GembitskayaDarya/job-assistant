package com.darya.jobassistant.integrations.notifier;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobNotificationFactoryTest {

    private final JobNotificationFactory factory = new JobNotificationFactory();

    @Test
    void create_preservesVacancyMetadataAndTheExactAnalysisValue() {
        UUID vacancyId = UUID.randomUUID();
        Company company = Company.builder().name("Acme Corp").build();
        Vacancy vacancy = Vacancy.builder()
                .id(vacancyId)
                .company(company)
                .title("Senior Backend Engineer")
                .url("https://example.com/job-1")
                .source("remoteok")
                .build();
        JobAnalysis analysis = new JobAnalysis(
                85, List.of("Strong Java skills"), List.of("No AWS mentioned"),
                List.of("Kafka"), List.of("Terraform"),
                "6 years vs. no stated requirement.", "Remote preference matches.", "Good match");

        JobNotification notification = factory.create(vacancy, analysis, 12345L);

        assertThat(notification.vacancyId()).isEqualTo(vacancyId);
        assertThat(notification.recipientChatId()).isEqualTo(12345L);
        assertThat(notification.title()).isEqualTo("Senior Backend Engineer");
        assertThat(notification.companyName()).isEqualTo("Acme Corp");
        assertThat(notification.url()).isEqualTo("https://example.com/job-1");
        // The exact same JobAnalysis value is carried through, not copied field by field.
        assertThat(notification.analysis()).isEqualTo(analysis);
    }

    @Test
    void create_requiredAndPreferredGapsAreNotMerged() {
        Vacancy vacancy = Vacancy.builder()
                .id(UUID.randomUUID())
                .company(Company.builder().name("Acme").build())
                .title("Backend Engineer")
                .url("https://example.com/job-2")
                .source("remoteok")
                .build();
        JobAnalysis analysis = new JobAnalysis(
                70, List.of(), List.of(), List.of("Kubernetes"), List.of("GraphQL"),
                "Not assessed.", "Not assessed.", "Summary");

        JobNotification notification = factory.create(vacancy, analysis, 999L);

        assertThat(notification.analysis().missingRequiredSkills()).containsExactly("Kubernetes");
        assertThat(notification.analysis().missingPreferredSkills()).containsExactly("GraphQL");
    }
}

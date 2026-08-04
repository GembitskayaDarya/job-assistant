package com.darya.jobassistant.careerhistory.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidates.entity.CandidateProfileEntity;
import com.darya.jobassistant.candidates.repository.CandidateProfileRepository;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryConcurrentModificationException;
import com.darya.jobassistant.careerhistory.aggregate.CareerPosition;
import com.darya.jobassistant.careerhistory.aggregate.CareerProject;
import com.darya.jobassistant.careerhistory.aggregate.CareerTechnology;
import com.darya.jobassistant.careerhistory.repository.CareerCompanyRepository;
import com.darya.jobassistant.careerhistory.repository.CareerHistoryRepository;
import com.darya.jobassistant.careerhistory.repository.CareerPositionAchievementRepository;
import com.darya.jobassistant.careerhistory.repository.CareerPositionRepository;
import com.darya.jobassistant.careerhistory.repository.CareerPositionResponsibilityRepository;
import com.darya.jobassistant.careerhistory.repository.CareerProjectAchievementRepository;
import com.darya.jobassistant.careerhistory.repository.CareerProjectRepository;
import com.darya.jobassistant.careerhistory.repository.CareerProjectResponsibilityRepository;
import com.darya.jobassistant.careerhistory.repository.CareerProjectTechnologyRepository;
import com.darya.jobassistant.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 9 Step 6 concurrency scenario: two independent domain snapshots loaded at the same
 * version {@code N}, each changing a different nested child. The first writer to save wins and
 * moves the aggregate to {@code N + 1}; the second, still holding the stale version {@code N},
 * must fail with {@link CareerHistoryConcurrentModificationException} rather than silently
 * overwriting the first writer's change - and must fail before deleting anything (see {@code
 * CareerHistoryRepositoryAdapterTest#staleChildOnlyUpdate_failsBeforeDeletingExistingChildRows_firstWritersGraphSurvives}
 * for that same guarantee proven directly against the adapter).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
class CareerHistoryRepositoryAdapterConcurrencyTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private CareerHistoryRepository careerHistoryRepository;

    @Autowired
    private CareerCompanyRepository careerCompanyRepository;

    @Autowired
    private CareerPositionRepository careerPositionRepository;

    @Autowired
    private CareerPositionResponsibilityRepository careerPositionResponsibilityRepository;

    @Autowired
    private CareerPositionAchievementRepository careerPositionAchievementRepository;

    @Autowired
    private CareerProjectRepository careerProjectRepository;

    @Autowired
    private CareerProjectResponsibilityRepository careerProjectResponsibilityRepository;

    @Autowired
    private CareerProjectAchievementRepository careerProjectAchievementRepository;

    @Autowired
    private CareerProjectTechnologyRepository careerProjectTechnologyRepository;

    @Autowired
    private EntityManager entityManager;

    private CareerHistoryRepositoryAdapter adapter() {
        return new CareerHistoryRepositoryAdapter(
                careerHistoryRepository, careerCompanyRepository, careerPositionRepository,
                careerPositionResponsibilityRepository, careerPositionAchievementRepository,
                careerProjectRepository, careerProjectResponsibilityRepository, careerProjectAchievementRepository,
                careerProjectTechnologyRepository, candidateProfileRepository, Clock.systemUTC(), entityManager);
    }

    @Test
    void twoIndependentSnapshotsAtTheSameVersion_secondStaleWriterFails_firstWritersGraphSurvives() {
        UUID profileId = candidateProfile("concurrency-" + UUID.randomUUID()).getId();
        CareerHistoryAggregate initial = adapter().save(aggregateWithTechnologyAndCompanyName(profileId, "Kafka", "Example Systems"));
        long versionN = initial.version();

        // Two independent snapshots, both still at version N.
        CareerHistoryAggregate firstWriterSnapshot = adapter().findByCandidateProfileId(profileId).orElseThrow();
        CareerHistoryAggregate secondWriterSnapshot = adapter().findByCandidateProfileId(profileId).orElseThrow();
        assertThat(firstWriterSnapshot.version()).isEqualTo(versionN);
        assertThat(secondWriterSnapshot.version()).isEqualTo(versionN);

        // First writer changes a nested technology and saves successfully.
        CareerHistoryAggregate firstWriterChange = withTechnologyName(firstWriterSnapshot, "PostgreSQL");
        CareerHistoryAggregate firstWriterResult = adapter().save(firstWriterChange);
        assertThat(firstWriterResult.version()).isEqualTo(versionN + 1);

        // Second writer, still holding stale version N, changes a different nested child (the company name).
        CareerHistoryAggregate secondWriterChange = withCompanyName(secondWriterSnapshot, "Zenith Systems");
        assertThatThrownBy(() -> adapter().save(secondWriterChange))
                .isInstanceOf(CareerHistoryConcurrentModificationException.class);

        CareerHistoryAggregate finalState = adapter().findByCandidateProfileId(profileId).orElseThrow();
        assertThat(finalState.version()).isEqualTo(versionN + 1);
        assertThat(finalState.companies().get(0).name()).isEqualTo("Example Systems");
        assertThat(finalState.companies().get(0).positions().get(0).projects().get(0).technologies())
                .extracting(CareerTechnology::name)
                .containsExactly("PostgreSQL");

        entityManager.flush();
        entityManager.clear();
        long companyRowCount = careerCompanyRepository.findAllByCareerHistoryIdOrderByDisplayOrderAsc(initial.id()).size();
        assertThat(companyRowCount).isEqualTo(1);
    }

    private CareerHistoryAggregate aggregateWithTechnologyAndCompanyName(UUID profileId, String technologyName, String companyName) {
        CareerProject project = new CareerProject(null, "Billing Platform", null, null, null, 0,
                List.of(), List.of(), List.of(new CareerTechnology(null, technologyName, null, 0)));
        CareerPosition position = new CareerPosition(null, "Demo Backend Engineer", null, null, null,
                LocalDate.of(2020, 1, 1), null, true, null, 0, List.of(), List.of(), List.of(project));
        CareerCompany company = new CareerCompany(null, companyName, null, null, null, null, 0, List.of(position));
        return new CareerHistoryAggregate(null, profileId, List.of(company), 0L);
    }

    private CareerHistoryAggregate withTechnologyName(CareerHistoryAggregate aggregate, String technologyName) {
        CareerCompany company = aggregate.companies().get(0);
        CareerPosition position = company.positions().get(0);
        CareerProject project = position.projects().get(0);
        CareerProject changedProject = new CareerProject(project.id(), project.name(), project.description(),
                project.startDate(), project.endDate(), project.displayOrder(), project.responsibilities(), project.achievements(),
                List.of(new CareerTechnology(null, technologyName, null, 0)));
        CareerPosition changedPosition = new CareerPosition(position.id(), position.title(), position.employmentType(),
                position.location(), position.workArrangement(), position.startDate(), position.endDate(), position.currentRole(),
                position.description(), position.displayOrder(), position.responsibilities(), position.achievements(), List.of(changedProject));
        CareerCompany changedCompany = new CareerCompany(company.id(), company.name(), company.website(), company.industry(),
                company.location(), company.description(), company.displayOrder(), List.of(changedPosition));
        return new CareerHistoryAggregate(aggregate.id(), aggregate.candidateProfileId(), List.of(changedCompany), aggregate.version());
    }

    private CareerHistoryAggregate withCompanyName(CareerHistoryAggregate aggregate, String companyName) {
        CareerCompany company = aggregate.companies().get(0);
        CareerCompany changedCompany = new CareerCompany(company.id(), companyName, company.website(), company.industry(),
                company.location(), company.description(), company.displayOrder(), company.positions());
        return new CareerHistoryAggregate(aggregate.id(), aggregate.candidateProfileId(), List.of(changedCompany), aggregate.version());
    }

    private CandidateProfileEntity candidateProfile(String profileKey) {
        return candidateProfileRepository.save(CandidateProfileEntity.builder()
                .profileKey(profileKey)
                .targetRole("Demo Backend Engineer")
                .seniority("Senior")
                .experienceYears(5)
                .build());
    }
}

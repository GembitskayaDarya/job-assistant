package com.darya.jobassistant.careerhistory.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.candidates.entity.CandidateProfileEntity;
import com.darya.jobassistant.candidates.repository.CandidateProfileRepository;
import com.darya.jobassistant.careerhistory.aggregate.CareerAchievement;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerPosition;
import com.darya.jobassistant.careerhistory.aggregate.CareerProject;
import com.darya.jobassistant.careerhistory.aggregate.CareerResponsibility;
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
 * Sprint 9 Step 6 correction: proves that replacing one Career History's graph through {@link
 * CareerHistoryRepositoryPort#save} can never touch a different Career History's rows - the real
 * risk a table-wide, no-argument {@code deleteAll()} would create. {@link
 * CareerHistoryRepositoryAdapter#deleteExistingGraph} instead calls {@link
 * CareerCompanyRepository#deleteAllByCareerHistoryId}, an explicit {@code WHERE
 * career_history_id = :id} bulk delete.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
class CareerHistoryRepositoryAdapterIsolationTest {

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
    void updatingOneCareerHistory_neverModifiesADifferentCareerHistory() {
        UUID profileIdA = candidateProfile("isolation-a-" + UUID.randomUUID()).getId();
        UUID profileIdB = candidateProfile("isolation-b-" + UUID.randomUUID()).getId();

        CareerHistoryAggregate savedA = adapter().save(fullAggregate(profileIdA, "Alpha Systems", "Kafka"));
        CareerHistoryAggregate savedB = adapter().save(fullAggregate(profileIdB, "Beta Systems", "PostgreSQL"));

        long versionB = savedB.version();
        CareerCompany companyB = savedB.companies().get(0);
        CareerPosition positionB = companyB.positions().get(0);
        CareerProject projectB = positionB.projects().get(0);
        UUID companyIdB = companyB.id();
        UUID positionIdB = positionB.id();
        UUID projectIdB = projectB.id();
        UUID responsibilityIdB = positionB.responsibilities().get(0).id();
        UUID achievementIdB = positionB.achievements().get(0).id();
        UUID technologyIdB = projectB.technologies().get(0).id();

        // Update A only - rename the company, add a technology, change the version.
        CareerCompany renamedCompanyA = new CareerCompany(
                savedA.companies().get(0).id(), "Alpha Systems Renamed", null, null, null, null, 0,
                savedA.companies().get(0).positions());
        adapter().save(new CareerHistoryAggregate(savedA.id(), profileIdA, List.of(renamedCompanyA), savedA.version()));

        entityManager.flush();
        entityManager.clear();

        CareerHistoryAggregate reloadedB = adapter().findByCandidateProfileId(profileIdB).orElseThrow();
        assertThat(reloadedB.version()).isEqualTo(versionB);
        assertThat(reloadedB.companies()).hasSize(1);
        CareerCompany reloadedCompanyB = reloadedB.companies().get(0);
        assertThat(reloadedCompanyB.id()).isEqualTo(companyIdB);
        assertThat(reloadedCompanyB.name()).isEqualTo("Beta Systems");
        CareerPosition reloadedPositionB = reloadedCompanyB.positions().get(0);
        assertThat(reloadedPositionB.id()).isEqualTo(positionIdB);
        assertThat(reloadedPositionB.responsibilities().get(0).id()).isEqualTo(responsibilityIdB);
        assertThat(reloadedPositionB.achievements().get(0).id()).isEqualTo(achievementIdB);
        CareerProject reloadedProjectB = reloadedPositionB.projects().get(0);
        assertThat(reloadedProjectB.id()).isEqualTo(projectIdB);
        assertThat(reloadedProjectB.technologies().get(0).id()).isEqualTo(technologyIdB);
        assertThat(reloadedProjectB.technologies().get(0).name()).isEqualTo("PostgreSQL");

        // Direct database counts for B's rows - proves no unscoped delete ever touched them.
        assertThat(careerCompanyRepository.findAllByCareerHistoryIdOrderByDisplayOrderAsc(savedB.id())).hasSize(1);
        assertThat(careerPositionRepository.findAllByCareerCompanyIdInOrderByDisplayOrderAsc(List.of(companyIdB))).hasSize(1);
        assertThat(careerProjectRepository.findAllByCareerPositionIdInOrderByDisplayOrderAsc(List.of(positionIdB))).hasSize(1);
        assertThat(careerProjectTechnologyRepository.findAllByCareerProjectIdInOrderByDisplayOrderAsc(List.of(projectIdB))).hasSize(1);

        // A really did change - proves this isn't a no-op test.
        CareerHistoryAggregate reloadedA = adapter().findByCandidateProfileId(profileIdA).orElseThrow();
        assertThat(reloadedA.companies().get(0).name()).isEqualTo("Alpha Systems Renamed");
        assertThat(reloadedA.version()).isEqualTo(savedA.version() + 1);
    }

    private CareerHistoryAggregate fullAggregate(UUID profileId, String companyName, String technologyName) {
        CareerProject project = new CareerProject(null, "Billing Platform", null, null, null, 0,
                List.of(), List.of(), List.of(new CareerTechnology(null, technologyName, null, 0)));
        CareerPosition position = new CareerPosition(null, "Demo Backend Engineer", null, null, null,
                LocalDate.of(2020, 1, 1), null, true, null, 0,
                List.of(new CareerResponsibility(null, "Own the billing service's reliability", 0)),
                List.of(new CareerAchievement(null, "Reduced synthetic processing latency by 20%", 0)),
                List.of(project));
        CareerCompany company = new CareerCompany(null, companyName, null, null, null, null, 0, List.of(position));
        return new CareerHistoryAggregate(null, profileId, List.of(company), 0L);
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

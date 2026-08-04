package com.darya.jobassistant.careerhistory.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidates.entity.CandidateProfileEntity;
import com.darya.jobassistant.candidates.repository.CandidateProfileRepository;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryRepositoryPort;
import com.darya.jobassistant.careerhistory.aggregate.CareerPosition;
import com.darya.jobassistant.careerhistory.aggregate.CareerProject;
import com.darya.jobassistant.careerhistory.aggregate.CareerTechnology;
import com.darya.jobassistant.careerhistory.repository.CareerCompanyRepository;
import com.darya.jobassistant.careerhistory.repository.CareerPositionRepository;
import com.darya.jobassistant.careerhistory.repository.CareerProjectRepository;
import com.darya.jobassistant.careerhistory.repository.CareerProjectTechnologyRepository;
import com.darya.jobassistant.config.ClockConfig;
import com.darya.jobassistant.config.JpaAuditingConfig;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 9 Step 6 correction: proves {@link CareerHistoryRepositoryAdapter#save}'s atomicity
 * against the real production write path - a genuine PostgreSQL rejection partway through {@link
 * CareerHistoryRepositoryAdapter#insertGraph}'s native inserts, not a mocked failure injected
 * before deletion even starts (the original version of this test). Registered as a real Spring
 * bean via {@code @Import} so {@code @Transactional} AOP actually applies (this class only checks
 * results otherwise, matching {@code CareerHistoryRepositoryAdapterTest}'s directly-constructed
 * convention).
 *
 * <p>The failure input is a domain-valid, database-invalid value: {@link
 * com.darya.jobassistant.careerhistory.aggregate.CareerCompany#industry} has no length validation
 * at the domain level (by design - field-length limits are the database's job, see V19), but
 * {@code career_company.industry} is {@code VARCHAR(150)}. A 200-character industry name passes
 * every domain constructor and fails only when PostgreSQL actually tries to store it. The update
 * supplies two companies so the ordering requirement is real, not incidental: the first company
 * (with its position, project, and technology) is fully inserted via native SQL before the second
 * company's own insert fails.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({JpaAuditingConfig.class, ClockConfig.class, CareerHistoryRepositoryAdapter.class})
class CareerHistoryRepositoryAdapterRollbackTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private CareerHistoryRepositoryPort careerHistoryRepositoryPort;

    @Autowired
    private CareerCompanyRepository careerCompanyRepository;

    @Autowired
    private CareerPositionRepository careerPositionRepository;

    @Autowired
    private CareerProjectRepository careerProjectRepository;

    @Autowired
    private CareerProjectTechnologyRepository careerProjectTechnologyRepository;

    /**
     * {@code @DataJpaTest} wraps each test method in its own transaction by default - a nested
     * {@code @Transactional} call (this test's real, AOP-proxied adapter bean) would just join
     * that ambient transaction rather than commit/roll back independently, so a "rolled back" read
     * immediately afterward would actually observe not-yet-rolled-back state. {@code
     * NOT_SUPPORTED} suspends the ambient transaction, matching {@code
     * CandidateProfileRepositoryTest}'s optimistic-locking test's exact convention for the same
     * reason.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void save_nativeInsertFailsAfterPartialGraphReplacement_rollsBackTheCompleteTransaction() {
        UUID profileId = candidateProfile("partial-rollback-" + UUID.randomUUID()).getId();
        CareerHistoryAggregate original = careerHistoryRepositoryPort.save(validAggregate(profileId, "Example Systems", "Kafka"));
        long originalVersion = original.version();
        UUID historyId = original.id();
        UUID originalCompanyId = original.companies().get(0).id();
        UUID originalPositionId = original.companies().get(0).positions().get(0).id();
        UUID originalProjectId = original.companies().get(0).positions().get(0).projects().get(0).id();
        UUID originalTechnologyId = original.companies().get(0).positions().get(0).projects().get(0).technologies().get(0).id();

        int companyRowsBefore = careerCompanyRepository.findAllByCareerHistoryIdOrderByDisplayOrderAsc(historyId).size();
        int positionRowsBefore = careerPositionRepository.findAllByCareerCompanyIdInOrderByDisplayOrderAsc(List.of(originalCompanyId)).size();
        int projectRowsBefore = careerProjectRepository.findAllByCareerPositionIdInOrderByDisplayOrderAsc(List.of(originalPositionId)).size();
        int technologyRowsBefore =
                careerProjectTechnologyRepository.findAllByCareerProjectIdInOrderByDisplayOrderAsc(List.of(originalProjectId)).size();

        // First company is entirely valid and will fully insert (company, position, project,
        // technology) before the second company's overlong industry fails its own insert.
        CareerCompany firstCompany = companyWithTechnology("Renamed Systems", "Renamed Backend Engineer", "PostgreSQL");
        CareerCompany secondCompanyWithInvalidIndustry = new CareerCompany(
                null, "Zenith Systems", null, "x".repeat(200), null, null, 1, List.of());
        CareerHistoryAggregate updateAttempt = new CareerHistoryAggregate(
                historyId, profileId, List.of(firstCompany, secondCompanyWithInvalidIndustry), originalVersion);

        // The underlying PostgreSQL rejection surfaces here as Hibernate's own native
        // org.hibernate.exception.DataException, not Spring's DataAccessException - this call
        // goes through a raw EntityManager native query, not a Spring Data repository method, so
        // Spring's persistence-exception-translation interceptor never gets a chance to see it.
        // What actually matters for this test is that *some* failure propagates and the
        // transaction rolls back, proven below - not its exact translated type.
        assertThatThrownBy(() -> careerHistoryRepositoryPort.save(updateAttempt)).isInstanceOf(RuntimeException.class);

        CareerHistoryAggregate reloaded = careerHistoryRepositoryPort.findByCandidateProfileId(profileId).orElseThrow();
        assertThat(reloaded.version()).isEqualTo(originalVersion);
        assertThat(reloaded.companies()).hasSize(1);
        CareerCompany reloadedCompany = reloaded.companies().get(0);
        assertThat(reloadedCompany.id()).isEqualTo(originalCompanyId);
        assertThat(reloadedCompany.name()).isEqualTo("Example Systems");
        CareerPosition reloadedPosition = reloadedCompany.positions().get(0);
        assertThat(reloadedPosition.id()).isEqualTo(originalPositionId);
        CareerProject reloadedProject = reloadedPosition.projects().get(0);
        assertThat(reloadedProject.id()).isEqualTo(originalProjectId);
        assertThat(reloadedProject.technologies().get(0).id()).isEqualTo(originalTechnologyId);
        assertThat(reloadedProject.technologies().get(0).name()).isEqualTo("Kafka");

        assertThat(careerCompanyRepository.findAllByCareerHistoryIdOrderByDisplayOrderAsc(historyId).size()).isEqualTo(companyRowsBefore);
        assertThat(careerPositionRepository.findAllByCareerCompanyIdInOrderByDisplayOrderAsc(List.of(originalCompanyId)).size())
                .isEqualTo(positionRowsBefore);
        assertThat(careerProjectRepository.findAllByCareerPositionIdInOrderByDisplayOrderAsc(List.of(originalPositionId)).size())
                .isEqualTo(projectRowsBefore);
        assertThat(careerProjectTechnologyRepository.findAllByCareerProjectIdInOrderByDisplayOrderAsc(List.of(originalProjectId)).size())
                .isEqualTo(technologyRowsBefore);
    }

    private CareerHistoryAggregate validAggregate(UUID profileId, String companyName, String technologyName) {
        CareerCompany company = companyWithTechnology(companyName, "Demo Backend Engineer", technologyName);
        return new CareerHistoryAggregate(null, profileId, List.of(company), 0L);
    }

    private CareerCompany companyWithTechnology(String companyName, String positionTitle, String technologyName) {
        CareerProject project = new CareerProject(null, "Billing Platform", null, null, null, 0,
                List.of(), List.of(), List.of(new CareerTechnology(null, technologyName, null, 0)));
        CareerPosition position = new CareerPosition(null, positionTitle, null, null, null,
                LocalDate.of(2020, 1, 1), null, true, null, 0, List.of(), List.of(), List.of(project));
        return new CareerCompany(null, companyName, null, null, null, null, 0, List.of(position));
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

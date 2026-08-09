package com.darya.jobassistant.careerhistory.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidates.entity.CandidateProfileEntity;
import com.darya.jobassistant.candidates.repository.CandidateProfileRepository;
import com.darya.jobassistant.careerhistory.aggregate.CareerAchievement;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAlreadyExistsException;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryCandidateProfileNotFoundException;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryConcurrentModificationException;
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
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 9 Step 6: proves {@link CareerHistoryRepositoryAdapter} against real PostgreSQL - level-
 * based graph loading, atomic full-graph replacement on save, child id preservation, and
 * aggregate-level optimistic locking. Mirrors {@code CandidateProfileRepositoryAdapterTest}'s
 * {@code @DataJpaTest}/Testcontainers setup, building the adapter directly (not autowired - a
 * plain {@code @Repository} class, not a Spring Data interface). {@code
 * CareerHistoryRepositoryAdapterConcurrencyTest} and {@code CareerHistoryRepositoryAdapterRollbackTest}
 * cover the concurrency and public-port-rollback scenarios separately.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class CareerHistoryRepositoryAdapterTest {

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

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private CareerHistoryRepositoryAdapter adapter() {
        return new CareerHistoryRepositoryAdapter(
                careerHistoryRepository, careerCompanyRepository, careerPositionRepository,
                careerPositionResponsibilityRepository, careerPositionAchievementRepository,
                careerProjectRepository, careerProjectResponsibilityRepository, careerProjectAchievementRepository,
                careerProjectTechnologyRepository, candidateProfileRepository, Clock.systemUTC(), entityManager);
    }

    // ==================== 1-11. Basic persistence, ordering, no-leak ====================

    @Test
    void findByCandidateProfileId_missingCareerHistory_returnsEmpty() {
        assertThat(adapter().findByCandidateProfileId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void save_completeNewGraph_persistsAndReturnsTheFullGraphWithIds() {
        UUID profileId = candidateProfile("new-graph-" + UUID.randomUUID()).getId();
        CareerHistoryAggregate toSave = fullAggregate(profileId);

        CareerHistoryAggregate saved = adapter().save(toSave);

        assertThat(saved.id()).isNotNull();
        assertThat(saved.version()).isZero();
        assertThat(saved.candidateProfileId()).isEqualTo(profileId);
        assertGraphMatchesFullAggregate(saved);

        Optional<CareerHistoryAggregate> reloaded = adapter().findByCandidateProfileId(profileId);
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().id()).isEqualTo(saved.id());
        assertGraphMatchesFullAggregate(reloaded.get());
    }

    @Test
    void save_everyIdInTheGraph_isPopulatedAfterCreate() {
        UUID profileId = candidateProfile("ids-" + UUID.randomUUID()).getId();

        CareerHistoryAggregate saved = adapter().save(fullAggregate(profileId));

        assertThat(saved.id()).isNotNull();
        CareerCompany company = saved.companies().get(0);
        assertThat(company.id()).isNotNull();
        CareerPosition position = company.positions().get(0);
        assertThat(position.id()).isNotNull();
        assertThat(position.responsibilities().get(0).id()).isNotNull();
        assertThat(position.achievements().get(0).id()).isNotNull();
        CareerProject project = position.projects().get(0);
        assertThat(project.id()).isNotNull();
        assertThat(project.responsibilities().get(0).id()).isNotNull();
        assertThat(project.achievements().get(0).id()).isNotNull();
        assertThat(project.technologies().get(0).id()).isNotNull();
    }

    @Test
    void companiesPositionsProjectsBulletsAndTechnologies_loadInDisplayOrder_notInsertionOrder() {
        UUID profileId = candidateProfile("ordering-" + UUID.randomUUID()).getId();
        CareerTechnology techB = new CareerTechnology(null, "Zenith SDK", null, 1);
        CareerTechnology techA = new CareerTechnology(null, "Kafka", null, 0);
        CareerResponsibility respB = new CareerResponsibility(null, "Second responsibility", 1);
        CareerResponsibility respA = new CareerResponsibility(null, "First responsibility", 0);
        CareerAchievement achB = new CareerAchievement(null, "Second achievement", 1);
        CareerAchievement achA = new CareerAchievement(null, "First achievement", 0);
        CareerProject projectB = new CareerProject(null, "Second Project", null, null, null, 1, List.of(), List.of(), List.of());
        CareerProject projectA = new CareerProject(
                null, "First Project", null, null, null, 0, List.of(respA, respB), List.of(achA, achB), List.of(techA, techB));
        CareerPosition positionB = new CareerPosition(null, "Second Role", null, null, null,
                LocalDate.of(2021, 1, 1), null, false, null, 1, List.of(), List.of(), List.of());
        CareerPosition positionA = new CareerPosition(null, "First Role", null, null, null,
                LocalDate.of(2019, 1, 1), LocalDate.of(2020, 1, 1), false, null, 0, List.of(), List.of(), List.of(projectA, projectB));
        CareerCompany companyB = new CareerCompany(null, "Zenith Systems", null, null, null, null, 1, List.of());
        CareerCompany companyA = new CareerCompany(null, "Example Systems", null, null, null, null, 0, List.of(positionA, positionB));

        // Supplied out of order on purpose - the domain constructors already sort, and loading must too.
        adapter().save(new CareerHistoryAggregate(null, profileId, List.of(companyB, companyA), 0L));

        CareerHistoryAggregate loaded = adapter().findByCandidateProfileId(profileId).orElseThrow();
        assertThat(loaded.companies()).extracting(CareerCompany::name).containsExactly("Example Systems", "Zenith Systems");
        List<CareerPosition> positions = loaded.companies().get(0).positions();
        assertThat(positions).extracting(CareerPosition::title).containsExactly("First Role", "Second Role");
        List<CareerProject> projects = positions.get(0).projects();
        assertThat(projects).extracting(CareerProject::name).containsExactly("First Project", "Second Project");
        CareerProject loadedProjectA = projects.get(0);
        assertThat(loadedProjectA.responsibilities()).extracting(CareerResponsibility::text)
                .containsExactly("First responsibility", "Second responsibility");
        assertThat(loadedProjectA.achievements()).extracting(CareerAchievement::text)
                .containsExactly("First achievement", "Second achievement");
        assertThat(loadedProjectA.technologies()).extracting(CareerTechnology::name).containsExactly("Kafka", "Zenith SDK");
    }

    @Test
    void loadedGraph_containsOnlyDomainTypes_noJpaEntityOrProxyLeaksThrough() {
        UUID profileId = candidateProfile("no-leak-" + UUID.randomUUID()).getId();
        adapter().save(fullAggregate(profileId));

        CareerHistoryAggregate loaded = adapter().findByCandidateProfileId(profileId).orElseThrow();

        assertThat(loaded).isExactlyInstanceOf(CareerHistoryAggregate.class);
        assertThat(loaded.companies().get(0)).isExactlyInstanceOf(CareerCompany.class);
        assertThat(loaded.companies().get(0).positions().get(0)).isExactlyInstanceOf(CareerPosition.class);
    }

    /**
     * Requirement 12: proves the level-based loading strategy is genuinely bounded by graph
     * levels, not graph size, using Hibernate's own query-execution counter rather than just
     * documentation - a graph with two companies, each with two positions, each with one project,
     * still issues at most nine {@code SELECT}s (root + 8 child levels) on load.
     */
    @Test
    void loading_multiLevelGraph_issuesABoundedQueryCountRegardlessOfGraphSize() {
        UUID profileId = candidateProfile("bounded-queries-" + UUID.randomUUID()).getId();
        CareerProject project = new CareerProject(null, "Billing Platform", null, null, null, 0,
                List.of(new CareerResponsibility(null, "Design the event schema", 0)),
                List.of(new CareerAchievement(null, "Cut duplicate events to zero", 0)),
                List.of(new CareerTechnology(null, "Kafka", null, 0)));
        CareerPosition positionTemplate1 = new CareerPosition(null, "Role One", null, null, null,
                LocalDate.of(2019, 1, 1), LocalDate.of(2020, 1, 1), false, null, 0, List.of(), List.of(), List.of(project));
        CareerPosition positionTemplate2 = new CareerPosition(null, "Role Two", null, null, null,
                LocalDate.of(2020, 1, 1), null, true, null, 1, List.of(), List.of(), List.of());
        CareerCompany companyA = new CareerCompany(null, "Example Systems", null, null, null, null, 0,
                List.of(positionTemplate1, positionTemplate2));
        CareerCompany companyB = new CareerCompany(null, "Zenith Systems", null, null, null, null, 1,
                List.of(positionTemplate1, positionTemplate2));
        adapter().save(new CareerHistoryAggregate(null, profileId, List.of(companyA, companyB), 0L));
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        adapter().findByCandidateProfileId(profileId);

        assertThat(statistics.getQueryExecutionCount()).isLessThanOrEqualTo(9);
    }

    /**
     * Proves the import-validator fix's persistence side: two positions at the same company, each
     * with a project of the same name (the real "same continuous project, position title changed"
     * scenario), both persist as distinct rows under {@code uk_career_project_position_id_name}
     * (scoped to {@code career_position_id}, not {@code career_company_id}), reload correctly, and
     * keep distinct ids under their own owning position.
     */
    @Test
    void twoPositionsInSameCompany_canEachPersistAProjectWithTheSameName() {
        UUID profileId = candidateProfile("same-project-name-" + UUID.randomUUID()).getId();
        CareerProject projectInPositionA = new CareerProject(null, "Shared Platform", null, null, null, 0, List.of(), List.of(), List.of());
        CareerProject projectInPositionB = new CareerProject(null, "Shared Platform", null, null, null, 0, List.of(), List.of(), List.of());
        CareerPosition positionA = new CareerPosition(null, "Senior Backend Engineer", null, null, null,
                LocalDate.of(2020, 1, 1), LocalDate.of(2022, 1, 1), false, null, 0, List.of(), List.of(), List.of(projectInPositionA));
        CareerPosition positionB = new CareerPosition(null, "Component Lead", null, null, null,
                LocalDate.of(2022, 1, 1), null, true, null, 1, List.of(), List.of(), List.of(projectInPositionB));
        CareerCompany company = new CareerCompany(null, "Example Systems", null, null, null, null, 0, List.of(positionA, positionB));

        CareerHistoryAggregate saved = adapter().save(new CareerHistoryAggregate(null, profileId, List.of(company), 0L));

        CareerPosition savedPositionA = saved.companies().get(0).positions().get(0);
        CareerPosition savedPositionB = saved.companies().get(0).positions().get(1);
        UUID projectIdA = savedPositionA.projects().get(0).id();
        UUID projectIdB = savedPositionB.projects().get(0).id();
        assertThat(projectIdA).isNotNull();
        assertThat(projectIdB).isNotNull();
        assertThat(projectIdA).isNotEqualTo(projectIdB);

        entityManager.flush();
        entityManager.clear();
        CareerHistoryAggregate reloaded = adapter().findByCandidateProfileId(profileId).orElseThrow();
        CareerPosition reloadedPositionA = reloaded.companies().get(0).positions().get(0);
        CareerPosition reloadedPositionB = reloaded.companies().get(0).positions().get(1);
        assertThat(reloadedPositionA.title()).isEqualTo("Senior Backend Engineer");
        assertThat(reloadedPositionB.title()).isEqualTo("Component Lead");
        assertThat(reloadedPositionA.projects()).extracting(CareerProject::name).containsExactly("Shared Platform");
        assertThat(reloadedPositionB.projects()).extracting(CareerProject::name).containsExactly("Shared Platform");
        assertThat(reloadedPositionA.projects().get(0).id()).isEqualTo(projectIdA);
        assertThat(reloadedPositionB.projects().get(0).id()).isEqualTo(projectIdB);
        assertThat(careerProjectRepository.findAllByCareerPositionIdOrderByDisplayOrderAsc(reloadedPositionA.id())).hasSize(1);
        assertThat(careerProjectRepository.findAllByCareerPositionIdOrderByDisplayOrderAsc(reloadedPositionB.id())).hasSize(1);
    }

    // ==================== 13-18. Child-only updates increment root version ====================

    @Test
    void updatingOnlyACompany_incrementsRootVersion() {
        UUID profileId = candidateProfile("update-company-" + UUID.randomUUID()).getId();
        CareerHistoryAggregate saved = adapter().save(minimalAggregate(profileId));
        CareerCompany renamed = withCompanyName(saved.companies().get(0), "Renamed Systems");

        CareerHistoryAggregate updated = adapter().save(withCompanies(saved, List.of(renamed)));

        assertThat(updated.version()).isEqualTo(saved.version() + 1);
        assertThat(updated.companies().get(0).name()).isEqualTo("Renamed Systems");
    }

    @Test
    void updatingOnlyAPosition_incrementsRootVersion() {
        UUID profileId = candidateProfile("update-position-" + UUID.randomUUID()).getId();
        CareerHistoryAggregate saved = adapter().save(minimalAggregate(profileId));
        CareerCompany company = saved.companies().get(0);
        CareerPosition renamedPosition = withPositionTitle(company.positions().get(0), "Renamed Title");

        CareerHistoryAggregate updated = adapter().save(withCompanies(saved, List.of(withPositions(company, List.of(renamedPosition)))));

        assertThat(updated.version()).isEqualTo(saved.version() + 1);
        assertThat(updated.companies().get(0).positions().get(0).title()).isEqualTo("Renamed Title");
    }

    @Test
    void updatingOnlyAProject_incrementsRootVersion() {
        UUID profileId = candidateProfile("update-project-" + UUID.randomUUID()).getId();
        CareerHistoryAggregate saved = adapter().save(aggregateWithProject(profileId));
        CareerCompany company = saved.companies().get(0);
        CareerPosition position = company.positions().get(0);
        CareerProject renamedProject = withProjectName(position.projects().get(0), "Renamed Project");

        CareerHistoryAggregate updated = adapter().save(
                withCompanies(saved, List.of(withPositions(company, List.of(withProjects(position, List.of(renamedProject)))))));

        assertThat(updated.version()).isEqualTo(saved.version() + 1);
        assertThat(updated.companies().get(0).positions().get(0).projects().get(0).name()).isEqualTo("Renamed Project");
    }

    @Test
    void updatingOnlyAResponsibility_incrementsRootVersion() {
        UUID profileId = candidateProfile("update-responsibility-" + UUID.randomUUID()).getId();
        CareerHistoryAggregate saved = adapter().save(aggregateWithProject(profileId));
        CareerCompany company = saved.companies().get(0);
        CareerPosition position = company.positions().get(0);
        CareerProject project = position.projects().get(0);
        CareerProject changedProject = new CareerProject(
                project.id(), project.name(), project.description(), project.startDate(), project.endDate(), project.displayOrder(),
                List.of(new CareerResponsibility(null, "Updated responsibility", 0)), project.achievements(), project.technologies());

        CareerHistoryAggregate updated = adapter().save(
                withCompanies(saved, List.of(withPositions(company, List.of(withProjects(position, List.of(changedProject)))))));

        assertThat(updated.version()).isEqualTo(saved.version() + 1);
    }

    @Test
    void updatingOnlyAnAchievement_incrementsRootVersion() {
        UUID profileId = candidateProfile("update-achievement-" + UUID.randomUUID()).getId();
        CareerHistoryAggregate saved = adapter().save(aggregateWithProject(profileId));
        CareerCompany company = saved.companies().get(0);
        CareerPosition position = company.positions().get(0);
        CareerProject project = position.projects().get(0);
        CareerProject changedProject = new CareerProject(
                project.id(), project.name(), project.description(), project.startDate(), project.endDate(), project.displayOrder(),
                project.responsibilities(), List.of(new CareerAchievement(null, "Updated achievement", 0)), project.technologies());

        CareerHistoryAggregate updated = adapter().save(
                withCompanies(saved, List.of(withPositions(company, List.of(withProjects(position, List.of(changedProject)))))));

        assertThat(updated.version()).isEqualTo(saved.version() + 1);
    }

    @Test
    void updatingOnlyATechnology_incrementsRootVersion() {
        UUID profileId = candidateProfile("update-technology-" + UUID.randomUUID()).getId();
        CareerHistoryAggregate saved = adapter().save(aggregateWithProject(profileId));
        CareerCompany company = saved.companies().get(0);
        CareerPosition position = company.positions().get(0);
        CareerProject project = position.projects().get(0);
        CareerProject changedProject = new CareerProject(
                project.id(), project.name(), project.description(), project.startDate(), project.endDate(), project.displayOrder(),
                project.responsibilities(), project.achievements(), List.of(new CareerTechnology(null, "PostgreSQL", null, 0)));

        CareerHistoryAggregate updated = adapter().save(
                withCompanies(saved, List.of(withPositions(company, List.of(withProjects(position, List.of(changedProject)))))));

        assertThat(updated.version()).isEqualTo(saved.version() + 1);
        assertThat(updated.companies().get(0).positions().get(0).projects().get(0).technologies().get(0).name())
                .isEqualTo("PostgreSQL");
    }

    // ==================== 19-22, 36. Replacement removes old rows ====================

    @Test
    void removingACompany_removesItsCompleteDescendantGraph() {
        UUID profileId = candidateProfile("remove-company-" + UUID.randomUUID()).getId();
        CareerHistoryAggregate saved = adapter().save(aggregateWithProject(profileId));
        UUID companyId = saved.companies().get(0).id();
        UUID positionId = saved.companies().get(0).positions().get(0).id();

        adapter().save(withCompanies(saved, List.of()));

        entityManager.flush();
        entityManager.clear();
        assertThat(careerCompanyRepository.findById(companyId)).isEmpty();
        assertThat(careerPositionRepository.findById(positionId)).isEmpty();
    }

    @Test
    void removingAPosition_removesItsCompleteDescendantGraph() {
        UUID profileId = candidateProfile("remove-position-" + UUID.randomUUID()).getId();
        CareerHistoryAggregate saved = adapter().save(aggregateWithProject(profileId));
        CareerCompany company = saved.companies().get(0);
        UUID positionId = company.positions().get(0).id();
        UUID projectId = company.positions().get(0).projects().get(0).id();

        adapter().save(withCompanies(saved, List.of(withPositions(company, List.of()))));

        entityManager.flush();
        entityManager.clear();
        assertThat(careerPositionRepository.findById(positionId)).isEmpty();
        assertThat(careerProjectRepository.findById(projectId)).isEmpty();
    }

    @Test
    void removingAProject_removesItsDetails() {
        UUID profileId = candidateProfile("remove-project-" + UUID.randomUUID()).getId();
        CareerHistoryAggregate saved = adapter().save(aggregateWithProject(profileId));
        CareerCompany company = saved.companies().get(0);
        CareerPosition position = company.positions().get(0);
        UUID projectId = position.projects().get(0).id();
        UUID technologyId = position.projects().get(0).technologies().get(0).id();

        adapter().save(withCompanies(saved, List.of(withPositions(company, List.of(withProjects(position, List.of()))))));

        entityManager.flush();
        entityManager.clear();
        assertThat(careerProjectRepository.findById(projectId)).isEmpty();
        assertThat(careerProjectTechnologyRepository.findById(technologyId)).isEmpty();
    }

    @Test
    void replacingTheGraph_doesNotLeaveOldRows_rowCountStaysStable() {
        UUID profileId = candidateProfile("no-leftovers-" + UUID.randomUUID()).getId();
        CareerHistoryAggregate saved = adapter().save(minimalAggregate(profileId));
        UUID historyId = saved.id();

        adapter().save(withCompanies(saved, List.of(withCompanyName(saved.companies().get(0), "Still One Company"))));

        entityManager.flush();
        entityManager.clear();
        long companyCount = careerCompanyRepository.findAllByCareerHistoryIdOrderByDisplayOrderAsc(historyId).size();
        assertThat(companyCount).isEqualTo(1);
    }

    /** Requirement 36: saving the identical supplied graph again (with the reloaded version) replaces, not appends. */
    @Test
    void savingTheSameGraphRepeatedly_doesNotCreateDuplicateRows() {
        UUID profileId = candidateProfile("repeated-save-" + UUID.randomUUID()).getId();
        CareerHistoryAggregate saved = adapter().save(minimalAggregate(profileId));

        CareerHistoryAggregate resaved = adapter().save(withCompanies(saved, saved.companies()));
        CareerHistoryAggregate resavedAgain = adapter().save(withCompanies(resaved, resaved.companies()));

        entityManager.flush();
        entityManager.clear();
        assertThat(careerCompanyRepository.findAllByCareerHistoryIdOrderByDisplayOrderAsc(saved.id())).hasSize(1);
        assertThat(resavedAgain.version()).isEqualTo(saved.version() + 2);
    }

    // ==================== 23-24. Child identity ====================

    @Test
    void existingChildIds_arePreservedDuringUpdate_andNewChildrenReceiveIds() {
        UUID profileId = candidateProfile("identity-" + UUID.randomUUID()).getId();
        CareerHistoryAggregate saved = adapter().save(minimalAggregate(profileId));
        CareerCompany existingCompany = saved.companies().get(0);
        UUID existingCompanyId = existingCompany.id();
        CareerCompany newCompany = new CareerCompany(null, "New Systems", null, null, null, null, 1, List.of());

        CareerHistoryAggregate updated = adapter().save(withCompanies(saved, List.of(existingCompany, newCompany)));

        CareerCompany reloadedExisting = updated.companies().stream()
                .filter(c -> c.name().equals("Example Systems")).findFirst().orElseThrow();
        CareerCompany reloadedNew = updated.companies().stream()
                .filter(c -> c.name().equals("New Systems")).findFirst().orElseThrow();
        assertThat(reloadedExisting.id()).isEqualTo(existingCompanyId);
        assertThat(reloadedNew.id()).isNotNull();
        assertThat(reloadedNew.id()).isNotEqualTo(existingCompanyId);
    }

    /**
     * Sprint 9 Step 6 correction: proves {@code save()}'s own returned aggregate is not built
     * from stale/removed persistence-context state left over from {@link #deleteExistingGraph}'s
     * delete-then-native-reinsert with reused child ids - it must exactly match a completely
     * independent reload afterward. {@link CareerHistoryAggregate} and every nested type are
     * records, so {@code equals()} is real structural equality, not identity.
     */
    @Test
    void save_returnsAnAggregateIdenticalToAFreshIndependentReload_whenReusingChildIds() {
        UUID profileId = candidateProfile("persistence-context-consistency-" + UUID.randomUUID()).getId();
        CareerHistoryAggregate saved = adapter().save(aggregateWithProject(profileId));
        CareerCompany company = saved.companies().get(0);
        CareerPosition position = company.positions().get(0);
        CareerProject project = position.projects().get(0);
        // Same preserved ids throughout, only the technology's name changes.
        CareerProject changedProject = new CareerProject(project.id(), project.name(), project.description(),
                project.startDate(), project.endDate(), project.displayOrder(), project.responsibilities(), project.achievements(),
                List.of(new CareerTechnology(project.technologies().get(0).id(), "PostgreSQL", null, 0)));
        CareerHistoryAggregate updateAttempt = withCompanies(
                saved, List.of(withPositions(company, List.of(withProjects(position, List.of(changedProject))))));

        CareerHistoryAggregate returned = adapter().save(updateAttempt);

        entityManager.flush();
        entityManager.clear();
        CareerHistoryAggregate freshlyReloaded = adapter().findByCandidateProfileId(profileId).orElseThrow();

        assertThat(freshlyReloaded).isEqualTo(returned);
        assertThat(freshlyReloaded.version()).isEqualTo(saved.version() + 1);
        assertThat(freshlyReloaded.companies().get(0).positions().get(0).projects().get(0).technologies().get(0).name())
                .isEqualTo("PostgreSQL");
    }

    // ==================== 26. Empty companies ====================

    @Test
    void savingAnAggregateWithEmptyCompanies_isAllowed() {
        UUID profileId = candidateProfile("empty-companies-" + UUID.randomUUID()).getId();

        CareerHistoryAggregate saved = adapter().save(new CareerHistoryAggregate(null, profileId, List.of(), 0L));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.companies()).isEmpty();
        assertThat(adapter().findByCandidateProfileId(profileId)).isPresent();
    }

    // ==================== 27-28. Version increments exactly once / stale update fails ====================

    @Test
    void normalUpdate_incrementsVersionExactlyOnce() {
        UUID profileId = candidateProfile("version-once-" + UUID.randomUUID()).getId();
        CareerHistoryAggregate saved = adapter().save(minimalAggregate(profileId));

        CareerHistoryAggregate updated = adapter().save(withCompanies(saved, saved.companies()));

        assertThat(updated.version()).isEqualTo(1L);
    }

    @Test
    void staleUpdate_throwsCareerHistoryConcurrentModificationException() {
        UUID profileId = candidateProfile("stale-" + UUID.randomUUID()).getId();
        CareerHistoryAggregate saved = adapter().save(minimalAggregate(profileId));
        adapter().save(withCompanies(saved, saved.companies())); // version now 1

        CareerHistoryAggregate staleAttempt = withCompanies(saved, saved.companies()); // still carries version 0

        assertThatThrownBy(() -> adapter().save(staleAttempt))
                .isInstanceOf(CareerHistoryConcurrentModificationException.class);
    }

    /** Requirements 29-30: a stale write fails before any destructive delete, and the first writer's complete graph survives untouched. */
    @Test
    void staleChildOnlyUpdate_failsBeforeDeletingExistingChildRows_firstWritersGraphSurvives() {
        UUID profileId = candidateProfile("stale-child-" + UUID.randomUUID()).getId();
        CareerHistoryAggregate saved = adapter().save(minimalAggregate(profileId));
        CareerHistoryAggregate firstWriterResult = adapter().save(
                withCompanies(saved, List.of(withCompanyName(saved.companies().get(0), "First Writer Systems"))));

        CareerCompany secondWriterCompany = withCompanyName(saved.companies().get(0), "Second Writer Systems");
        CareerHistoryAggregate staleSecondWrite = withCompanies(saved, List.of(secondWriterCompany)); // stale version 0

        assertThatThrownBy(() -> adapter().save(staleSecondWrite))
                .isInstanceOf(CareerHistoryConcurrentModificationException.class);

        CareerHistoryAggregate reloaded = adapter().findByCandidateProfileId(profileId).orElseThrow();
        assertThat(reloaded.version()).isEqualTo(firstWriterResult.version());
        assertThat(reloaded.companies()).extracting(CareerCompany::name).containsExactly("First Writer Systems");
    }

    // ==================== 31-33. Multi-profile support and rejected references ====================

    @Test
    void twoCareerHistories_forDifferentCandidateProfiles_areBothSupported() {
        UUID profileIdA = candidateProfile("multi-profile-a-" + UUID.randomUUID()).getId();
        UUID profileIdB = candidateProfile("multi-profile-b-" + UUID.randomUUID()).getId();

        adapter().save(minimalAggregate(profileIdA));
        adapter().save(minimalAggregate(profileIdB));

        assertThat(adapter().findByCandidateProfileId(profileIdA)).isPresent();
        assertThat(adapter().findByCandidateProfileId(profileIdB)).isPresent();
    }

    @Test
    void secondCareerHistory_forSameCandidateProfile_isRejected() {
        UUID profileId = candidateProfile("second-history-" + UUID.randomUUID()).getId();
        adapter().save(minimalAggregate(profileId));

        assertThatThrownBy(() -> adapter().save(minimalAggregate(profileId)))
                .isInstanceOf(CareerHistoryAlreadyExistsException.class);
    }

    @Test
    void invalidCandidateProfileReference_isRejected() {
        UUID nonexistentProfileId = UUID.randomUUID();

        assertThatThrownBy(() -> adapter().save(minimalAggregate(nonexistentProfileId)))
                .isInstanceOf(CareerHistoryCandidateProfileNotFoundException.class);
    }

    // ==================== Helpers ====================

    private void assertGraphMatchesFullAggregate(CareerHistoryAggregate aggregate) {
        assertThat(aggregate.companies()).hasSize(1);
        CareerCompany company = aggregate.companies().get(0);
        assertThat(company.name()).isEqualTo("Example Systems");
        assertThat(company.positions()).hasSize(1);
        CareerPosition position = company.positions().get(0);
        assertThat(position.title()).isEqualTo("Demo Backend Engineer");
        assertThat(position.responsibilities()).extracting(CareerResponsibility::text).containsExactly("Own the billing service's reliability");
        assertThat(position.achievements()).extracting(CareerAchievement::text).containsExactly("Reduced synthetic processing latency by 20%");
        assertThat(position.projects()).hasSize(1);
        CareerProject project = position.projects().get(0);
        assertThat(project.name()).isEqualTo("Billing Platform");
        assertThat(project.responsibilities()).extracting(CareerResponsibility::text).containsExactly("Design the event schema");
        assertThat(project.achievements()).extracting(CareerAchievement::text).containsExactly("Cut duplicate events to zero");
        assertThat(project.technologies()).extracting(CareerTechnology::name).containsExactly("Kafka");
    }

    private CareerHistoryAggregate fullAggregate(UUID profileId) {
        CareerProject project = new CareerProject(null, "Billing Platform", null, null, null, 0,
                List.of(new CareerResponsibility(null, "Design the event schema", 0)),
                List.of(new CareerAchievement(null, "Cut duplicate events to zero", 0)),
                List.of(new CareerTechnology(null, "Kafka", null, 0)));
        CareerPosition position = new CareerPosition(null, "Demo Backend Engineer", "Full-time", "Remote", "Remote",
                LocalDate.of(2020, 1, 1), LocalDate.of(2023, 1, 1), false, "Owned the billing platform", 0,
                List.of(new CareerResponsibility(null, "Own the billing service's reliability", 0)),
                List.of(new CareerAchievement(null, "Reduced synthetic processing latency by 20%", 0)),
                List.of(project));
        CareerCompany company = new CareerCompany(null, "Example Systems", "https://example.test", "Software",
                "Remote", "A fictional example company", 0, List.of(position));
        return new CareerHistoryAggregate(null, profileId, List.of(company), 0L);
    }

    private CareerHistoryAggregate aggregateWithProject(UUID profileId) {
        CareerProject project = new CareerProject(null, "Billing Platform", null, null, null, 0,
                List.of(new CareerResponsibility(null, "Design the event schema", 0)),
                List.of(new CareerAchievement(null, "Cut duplicate events to zero", 0)),
                List.of(new CareerTechnology(null, "Kafka", null, 0)));
        CareerPosition position = new CareerPosition(null, "Demo Backend Engineer", null, null, null,
                LocalDate.of(2020, 1, 1), null, true, null, 0, List.of(), List.of(), List.of(project));
        CareerCompany company = new CareerCompany(null, "Example Systems", null, null, null, null, 0, List.of(position));
        return new CareerHistoryAggregate(null, profileId, List.of(company), 0L);
    }

    private CareerHistoryAggregate minimalAggregate(UUID profileId) {
        CareerPosition position = new CareerPosition(null, "Demo Backend Engineer", null, null, null,
                LocalDate.of(2020, 1, 1), null, true, null, 0, List.of(), List.of(), List.of());
        CareerCompany company = new CareerCompany(null, "Example Systems", null, null, null, null, 0, List.of(position));
        return new CareerHistoryAggregate(null, profileId, List.of(company), 0L);
    }

    private CareerHistoryAggregate withCompanies(CareerHistoryAggregate aggregate, List<CareerCompany> companies) {
        return new CareerHistoryAggregate(aggregate.id(), aggregate.candidateProfileId(), companies, aggregate.version());
    }

    private CareerCompany withCompanyName(CareerCompany company, String name) {
        return new CareerCompany(company.id(), name, company.website(), company.industry(), company.location(),
                company.description(), company.displayOrder(), company.positions());
    }

    private CareerCompany withPositions(CareerCompany company, List<CareerPosition> positions) {
        return new CareerCompany(company.id(), company.name(), company.website(), company.industry(), company.location(),
                company.description(), company.displayOrder(), positions);
    }

    private CareerPosition withPositionTitle(CareerPosition position, String title) {
        return new CareerPosition(position.id(), title, position.employmentType(), position.location(), position.workArrangement(),
                position.startDate(), position.endDate(), position.currentRole(), position.description(), position.displayOrder(),
                position.responsibilities(), position.achievements(), position.projects());
    }

    private CareerPosition withProjects(CareerPosition position, List<CareerProject> projects) {
        return new CareerPosition(position.id(), position.title(), position.employmentType(), position.location(), position.workArrangement(),
                position.startDate(), position.endDate(), position.currentRole(), position.description(), position.displayOrder(),
                position.responsibilities(), position.achievements(), projects);
    }

    private CareerProject withProjectName(CareerProject project, String name) {
        return new CareerProject(project.id(), name, project.description(), project.startDate(), project.endDate(),
                project.displayOrder(), project.responsibilities(), project.achievements(), project.technologies());
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

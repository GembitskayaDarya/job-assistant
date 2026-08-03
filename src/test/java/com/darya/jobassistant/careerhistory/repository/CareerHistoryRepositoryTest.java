package com.darya.jobassistant.careerhistory.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.darya.jobassistant.candidates.entity.CandidateProfileEntity;
import com.darya.jobassistant.candidates.repository.CandidateProfileRepository;
import com.darya.jobassistant.careerhistory.entity.CareerCompanyEntity;
import com.darya.jobassistant.careerhistory.entity.CareerHistoryEntity;
import com.darya.jobassistant.careerhistory.entity.CareerPositionAchievementEntity;
import com.darya.jobassistant.careerhistory.entity.CareerPositionEntity;
import com.darya.jobassistant.careerhistory.entity.CareerPositionResponsibilityEntity;
import com.darya.jobassistant.careerhistory.entity.CareerProjectAchievementEntity;
import com.darya.jobassistant.careerhistory.entity.CareerProjectEntity;
import com.darya.jobassistant.careerhistory.entity.CareerProjectResponsibilityEntity;
import com.darya.jobassistant.careerhistory.entity.CareerProjectTechnologyEntity;
import com.darya.jobassistant.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 9 Step 5: proves the V19 Career History persistence foundation's database invariants
 * against real PostgreSQL - additive tables not yet used by any runtime workflow (no repository
 * port, no adapter, no application service - see V19's own header comment). Mirrors {@code
 * CandidateProfileRepositoryTest}'s (Step 1) structure and conventions exactly: one {@code
 * @DataJpaTest} class covering persistence, ordering, every constraint, cascade, and optimistic
 * locking for the whole owned graph, each CHECK-violation case in its own test method (a CHECK
 * violation aborts the whole PostgreSQL transaction - see that class's javadoc for why).
 *
 * <p>All test data is fictional: no real employer, client, project, or salary appears anywhere in
 * this file.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
class CareerHistoryRepositoryTest {

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
    private PlatformTransactionManager transactionManager;

    // ==================== 1-7. Valid persistence ====================

    @Test
    void careerHistory_persistedForAnExistingCandidateProfile() {
        CandidateProfileEntity profile = candidateProfile("root-" + UUID.randomUUID());

        CareerHistoryEntity saved = careerHistoryRepository.save(careerHistory(profile));
        entityManager.flush();
        entityManager.clear();

        CareerHistoryEntity reloaded = careerHistoryRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCandidateProfile().getId()).isEqualTo(profile.getId());
        assertThat(reloaded.getVersion()).isZero();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
        assertThat(careerHistoryRepository.findByCandidateProfileId(profile.getId())).isPresent();
    }

    @Test
    void company_canBePersisted() {
        CareerHistoryEntity history = careerHistoryRepository.save(careerHistory(candidateProfile("company-" + UUID.randomUUID())));

        CareerCompanyEntity saved = careerCompanyRepository.save(company(history, "Example Systems", 0));
        entityManager.flush();
        entityManager.clear();

        CareerCompanyEntity reloaded = careerCompanyRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Example Systems");
        assertThat(reloaded.getCareerHistory().getId()).isEqualTo(history.getId());
        assertThat(reloaded.getDisplayOrder()).isZero();
    }

    @Test
    void multiplePositions_canBePersistedForOneCompany() {
        CareerCompanyEntity acme = careerCompanyRepository.save(
                company(careerHistoryRepository.save(careerHistory(candidateProfile("multi-pos-" + UUID.randomUUID()))), "Example Systems", 0));

        careerPositionRepository.save(position(acme, "Demo Backend Engineer", 0,
                LocalDate.of(2020, 1, 1), LocalDate.of(2022, 1, 1), false));
        careerPositionRepository.save(position(acme, "Demo Senior Backend Engineer", 1,
                LocalDate.of(2022, 1, 1), null, true));
        entityManager.flush();
        entityManager.clear();

        List<CareerPositionEntity> positions = careerPositionRepository.findAllByCareerCompanyIdOrderByDisplayOrderAsc(acme.getId());
        assertThat(positions).hasSize(2);
        assertThat(positions.get(0).getTitle()).isEqualTo("Demo Backend Engineer");
        assertThat(positions.get(1).getTitle()).isEqualTo("Demo Senior Backend Engineer");
    }

    @Test
    void positionResponsibilitiesAndAchievements_canBePersisted() {
        CareerPositionEntity position = aPosition("bullets-" + UUID.randomUUID());

        careerPositionResponsibilityRepository.save(positionResponsibility(position, "Own the billing service's reliability", 0));
        careerPositionAchievementRepository.save(positionAchievement(position, "Reduced synthetic processing latency by 20%", 0));
        entityManager.flush();
        entityManager.clear();

        assertThat(careerPositionResponsibilityRepository.findAllByCareerPositionIdOrderByDisplayOrderAsc(position.getId()))
                .extracting(CareerPositionResponsibilityEntity::getResponsibilityText)
                .containsExactly("Own the billing service's reliability");
        assertThat(careerPositionAchievementRepository.findAllByCareerPositionIdOrderByDisplayOrderAsc(position.getId()))
                .extracting(CareerPositionAchievementEntity::getAchievementText)
                .containsExactly("Reduced synthetic processing latency by 20%");
    }

    @Test
    void projects_canBePersisted() {
        CareerPositionEntity position = aPosition("projects-" + UUID.randomUUID());

        careerProjectRepository.save(project(position, "Billing Platform", 0, null, null));
        entityManager.flush();
        entityManager.clear();

        List<CareerProjectEntity> projects = careerProjectRepository.findAllByCareerPositionIdOrderByDisplayOrderAsc(position.getId());
        assertThat(projects).hasSize(1);
        assertThat(projects.get(0).getName()).isEqualTo("Billing Platform");
    }

    @Test
    void projectResponsibilitiesAchievementsAndTechnologies_canBePersisted() {
        CareerProjectEntity billingPlatform = aProject("project-bullets-" + UUID.randomUUID());

        careerProjectResponsibilityRepository.save(projectResponsibility(billingPlatform, "Design the event schema", 0));
        careerProjectAchievementRepository.save(projectAchievement(billingPlatform, "Cut duplicate events to zero", 0));
        careerProjectTechnologyRepository.save(technology(billingPlatform, "Kafka", 0));
        entityManager.flush();
        entityManager.clear();

        assertThat(careerProjectResponsibilityRepository.findAllByCareerProjectIdOrderByDisplayOrderAsc(billingPlatform.getId()))
                .extracting(CareerProjectResponsibilityEntity::getResponsibilityText)
                .containsExactly("Design the event schema");
        assertThat(careerProjectAchievementRepository.findAllByCareerProjectIdOrderByDisplayOrderAsc(billingPlatform.getId()))
                .extracting(CareerProjectAchievementEntity::getAchievementText)
                .containsExactly("Cut duplicate events to zero");
        assertThat(careerProjectTechnologyRepository.findAllByCareerProjectIdOrderByDisplayOrderAsc(billingPlatform.getId()))
                .extracting(CareerProjectTechnologyEntity::getTechnologyName)
                .containsExactly("Kafka");
    }

    @Test
    void orderedQueries_returnRowsByDisplayOrder_notInsertionOrPhysicalOrder() {
        CareerCompanyEntity company = careerCompanyRepository.save(
                company(careerHistoryRepository.save(careerHistory(candidateProfile("ordering-" + UUID.randomUUID()))), "Example Systems", 0));

        // Inserted out of display_order sequence on purpose.
        CareerPositionEntity second = careerPositionRepository.save(position(company, "Second Role", 1,
                LocalDate.of(2021, 1, 1), LocalDate.of(2022, 1, 1), false));
        CareerPositionEntity first = careerPositionRepository.save(position(company, "First Role", 0,
                LocalDate.of(2019, 1, 1), LocalDate.of(2021, 1, 1), false));
        entityManager.flush();
        entityManager.clear();

        List<CareerPositionEntity> positions = careerPositionRepository.findAllByCareerCompanyIdOrderByDisplayOrderAsc(company.getId());
        assertThat(positions).extracting(CareerPositionEntity::getTitle).containsExactly("First Role", "Second Role");
        assertThat(positions.get(0).getId()).isEqualTo(first.getId());
        assertThat(positions.get(1).getId()).isEqualTo(second.getId());
    }

    // ==================== 8-11. Root constraints ====================

    @Test
    void secondCareerHistory_forSameCandidateProfile_violatesUniqueConstraint() {
        CandidateProfileEntity profile = candidateProfile("dup-history-" + UUID.randomUUID());
        careerHistoryRepository.save(careerHistory(profile));
        entityManager.flush();

        DataIntegrityViolationException exception = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> careerHistoryRepository.saveAndFlush(careerHistory(profile)));

        assertThat(exception).isNotNull();
        assertThat(exception.getMostSpecificCause().getMessage()).contains("uk_career_history_candidate_profile_id");
    }

    @Test
    void careerHistory_withoutCandidateProfile_isRejected() {
        CareerHistoryEntity orphan = CareerHistoryEntity.builder().candidateProfile(null).build();

        assertThatThrownBy(() -> careerHistoryRepository.saveAndFlush(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameCompanyName_inDifferentCareerHistories_bothAccepted() {
        CareerHistoryEntity historyA = careerHistoryRepository.save(careerHistory(candidateProfile("company-a-" + UUID.randomUUID())));
        CareerHistoryEntity historyB = careerHistoryRepository.save(careerHistory(candidateProfile("company-b-" + UUID.randomUUID())));

        careerCompanyRepository.save(company(historyA, "Example Systems", 0));
        careerCompanyRepository.save(company(historyB, "Example Systems", 0));
        entityManager.flush();

        assertThat(careerCompanyRepository.findAllByCareerHistoryIdOrderByDisplayOrderAsc(historyA.getId())).hasSize(1);
        assertThat(careerCompanyRepository.findAllByCareerHistoryIdOrderByDisplayOrderAsc(historyB.getId())).hasSize(1);
    }

    /**
     * A different {@code displayOrder} (1, not 0) than the first company - otherwise this insert
     * would also collide with {@code uk_career_company_history_id_display_order}, and the test
     * would no longer reliably prove which constraint it intends to exercise.
     */
    @Test
    void duplicateCompanyName_inOneCareerHistory_violatesUniqueConstraint() {
        CareerHistoryEntity history = careerHistoryRepository.save(careerHistory(candidateProfile("dup-company-" + UUID.randomUUID())));
        careerCompanyRepository.save(company(history, "Example Systems", 0));
        entityManager.flush();

        DataIntegrityViolationException exception = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> careerCompanyRepository.saveAndFlush(company(history, "Example Systems", 1)));

        assertThat(exception).isNotNull();
        assertThat(exception.getMostSpecificCause().getMessage()).contains("uk_career_company_history_id_name");
    }

    // ==================== Company ordering (V20, Step 5 correction) ====================

    @Test
    void companies_returnedInDisplayOrder_notInsertionOrder() {
        CareerHistoryEntity history = careerHistoryRepository.save(careerHistory(candidateProfile("company-order-" + UUID.randomUUID())));

        // Inserted out of order, and deliberately with a name/insertion order that would sort
        // differently if ordering were accidentally derived from anything but display_order.
        careerCompanyRepository.save(company(history, "Zenith Systems", 1));
        careerCompanyRepository.save(company(history, "Alpha Robotics", 0));
        entityManager.flush();
        entityManager.clear();

        List<CareerCompanyEntity> companies = careerCompanyRepository.findAllByCareerHistoryIdOrderByDisplayOrderAsc(history.getId());
        assertThat(companies).extracting(CareerCompanyEntity::getName).containsExactly("Alpha Robotics", "Zenith Systems");
    }

    @Test
    void negativeCompanyDisplayOrder_violatesCheckConstraint() {
        CareerHistoryEntity history = careerHistoryRepository.save(careerHistory(candidateProfile("neg-company-order-" + UUID.randomUUID())));

        assertThatThrownBy(() -> careerCompanyRepository.saveAndFlush(company(history, "Example Systems", -1)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .extracting(ex -> ((DataIntegrityViolationException) ex).getMostSpecificCause().getMessage())
                .asString()
                .contains("chk_career_company_display_order_non_negative");
    }

    @Test
    void duplicateCompanyDisplayOrder_withinOneCareerHistory_violatesUniqueConstraint() {
        CareerHistoryEntity history = careerHistoryRepository.save(careerHistory(candidateProfile("dup-company-order-" + UUID.randomUUID())));
        careerCompanyRepository.save(company(history, "Example Systems", 0));
        entityManager.flush();

        DataIntegrityViolationException exception = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> careerCompanyRepository.saveAndFlush(company(history, "Zenith Systems", 0)));

        assertThat(exception).isNotNull();
        assertThat(exception.getMostSpecificCause().getMessage()).contains("uk_career_company_history_id_display_order");
    }

    @Test
    void sameCompanyDisplayOrder_inDifferentCareerHistories_bothAccepted() {
        CareerHistoryEntity historyA = careerHistoryRepository.save(careerHistory(candidateProfile("company-order-a-" + UUID.randomUUID())));
        CareerHistoryEntity historyB = careerHistoryRepository.save(careerHistory(candidateProfile("company-order-b-" + UUID.randomUUID())));

        careerCompanyRepository.save(company(historyA, "Example Systems", 0));
        careerCompanyRepository.save(company(historyB, "Zenith Systems", 0));
        entityManager.flush();

        assertThat(careerCompanyRepository.findAllByCareerHistoryIdOrderByDisplayOrderAsc(historyA.getId())).hasSize(1);
        assertThat(careerCompanyRepository.findAllByCareerHistoryIdOrderByDisplayOrderAsc(historyB.getId())).hasSize(1);
    }

    /**
     * Company order is independent of name, of the dates on the positions underneath it, and of
     * insertion order - "Zenith Systems" (alphabetically last, inserted first, holding the
     * earliest-dated position) still sorts before "Alpha Robotics" purely because {@code
     * display_order} says so.
     */
    @Test
    void companyOrder_isIndependentOfNamePositionDatesAndInsertionOrder() {
        CareerHistoryEntity history = careerHistoryRepository.save(careerHistory(candidateProfile("company-order-independence-" + UUID.randomUUID())));

        CareerCompanyEntity zenith = careerCompanyRepository.save(company(history, "Zenith Systems", 1));
        careerPositionRepository.save(position(zenith, "Demo Backend Engineer", 0, LocalDate.of(2015, 1, 1), LocalDate.of(2018, 1, 1), false));
        CareerCompanyEntity alpha = careerCompanyRepository.save(company(history, "Alpha Robotics", 0));
        careerPositionRepository.save(position(alpha, "Demo Senior Backend Engineer", 0, LocalDate.of(2022, 1, 1), null, true));
        entityManager.flush();
        entityManager.clear();

        List<CareerCompanyEntity> companies = careerCompanyRepository.findAllByCareerHistoryIdOrderByDisplayOrderAsc(history.getId());
        assertThat(companies).extracting(CareerCompanyEntity::getName).containsExactly("Alpha Robotics", "Zenith Systems");
    }

    // ==================== 12-17. Position constraints ====================

    @Test
    void blankPositionTitle_violatesCheckConstraint() {
        CareerCompanyEntity company = aCompany("blank-title-" + UUID.randomUUID());
        CareerPositionEntity position = position(company, "   ", 0, LocalDate.of(2020, 1, 1), null, false);

        assertThatThrownBy(() -> careerPositionRepository.saveAndFlush(position))
                .isInstanceOf(DataIntegrityViolationException.class)
                .extracting(ex -> ((DataIntegrityViolationException) ex).getMostSpecificCause().getMessage())
                .asString()
                .contains("chk_career_position_title_not_blank");
    }

    @Test
    void negativePositionDisplayOrder_violatesCheckConstraint() {
        CareerCompanyEntity company = aCompany("neg-order-" + UUID.randomUUID());
        CareerPositionEntity position = position(company, "Demo Backend Engineer", -1, LocalDate.of(2020, 1, 1), null, false);

        assertThatThrownBy(() -> careerPositionRepository.saveAndFlush(position))
                .isInstanceOf(DataIntegrityViolationException.class)
                .extracting(ex -> ((DataIntegrityViolationException) ex).getMostSpecificCause().getMessage())
                .asString()
                .contains("chk_career_position_display_order_non_negative");
    }

    @Test
    void duplicatePositionDisplayOrder_withinOneCompany_violatesUniqueConstraint() {
        CareerCompanyEntity company = aCompany("dup-pos-order-" + UUID.randomUUID());
        careerPositionRepository.save(position(company, "Demo Backend Engineer", 0, LocalDate.of(2020, 1, 1), null, true));
        entityManager.flush();

        DataIntegrityViolationException exception = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> careerPositionRepository.saveAndFlush(
                        position(company, "Demo Staff Backend Engineer", 0, LocalDate.of(2021, 1, 1), null, true)));

        assertThat(exception).isNotNull();
        assertThat(exception.getMostSpecificCause().getMessage()).contains("uk_career_position_company_id_display_order");
    }

    @Test
    void positionEndDateBeforeStartDate_violatesCheckConstraint() {
        CareerCompanyEntity company = aCompany("pos-bad-dates-" + UUID.randomUUID());
        CareerPositionEntity position = position(company, "Demo Backend Engineer", 0,
                LocalDate.of(2022, 1, 1), LocalDate.of(2021, 1, 1), false);

        assertThatThrownBy(() -> careerPositionRepository.saveAndFlush(position))
                .isInstanceOf(DataIntegrityViolationException.class)
                .extracting(ex -> ((DataIntegrityViolationException) ex).getMostSpecificCause().getMessage())
                .asString()
                .contains("chk_career_position_end_date_after_start_date");
    }

    @Test
    void currentRole_withEndDate_violatesCheckConstraint() {
        CareerCompanyEntity company = aCompany("current-with-end-" + UUID.randomUUID());
        CareerPositionEntity position = position(company, "Demo Backend Engineer", 0,
                LocalDate.of(2022, 1, 1), LocalDate.of(2023, 1, 1), true);

        assertThatThrownBy(() -> careerPositionRepository.saveAndFlush(position))
                .isInstanceOf(DataIntegrityViolationException.class)
                .extracting(ex -> ((DataIntegrityViolationException) ex).getMostSpecificCause().getMessage())
                .asString()
                .contains("chk_career_position_current_role_has_no_end_date");
    }

    @Test
    void nonCurrentRole_withNullEndDate_isAccepted() {
        CareerCompanyEntity company = aCompany("open-ended-" + UUID.randomUUID());
        CareerPositionEntity position = position(company, "Demo Backend Engineer", 0, LocalDate.of(2022, 1, 1), null, false);

        careerPositionRepository.save(position);
        entityManager.flush();
        entityManager.clear();

        CareerPositionEntity reloaded = careerPositionRepository.findAllByCareerCompanyIdOrderByDisplayOrderAsc(company.getId()).get(0);
        assertThat(reloaded.isCurrentRole()).isFalse();
        assertThat(reloaded.getEndDate()).isNull();
    }

    // ==================== 18-21. Project constraints ====================

    @Test
    void blankProjectName_violatesCheckConstraint() {
        CareerPositionEntity position = aPosition("blank-project-name-" + UUID.randomUUID());
        CareerProjectEntity project = project(position, "   ", 0, null, null);

        assertThatThrownBy(() -> careerProjectRepository.saveAndFlush(project))
                .isInstanceOf(DataIntegrityViolationException.class)
                .extracting(ex -> ((DataIntegrityViolationException) ex).getMostSpecificCause().getMessage())
                .asString()
                .contains("chk_career_project_name_not_blank");
    }

    @Test
    void duplicateProjectDisplayOrder_withinOnePosition_violatesUniqueConstraint() {
        CareerPositionEntity position = aPosition("dup-project-order-" + UUID.randomUUID());
        careerProjectRepository.save(project(position, "Billing Platform", 0, null, null));
        entityManager.flush();

        DataIntegrityViolationException exception = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> careerProjectRepository.saveAndFlush(project(position, "Notification Platform", 0, null, null)));

        assertThat(exception).isNotNull();
        assertThat(exception.getMostSpecificCause().getMessage()).contains("uk_career_project_position_id_display_order");
    }

    @Test
    void projectEndDateBeforeStartDate_violatesCheckConstraint() {
        CareerPositionEntity position = aPosition("project-bad-dates-" + UUID.randomUUID());
        CareerProjectEntity project = project(position, "Billing Platform", 0,
                LocalDate.of(2022, 6, 1), LocalDate.of(2022, 1, 1));

        assertThatThrownBy(() -> careerProjectRepository.saveAndFlush(project))
                .isInstanceOf(DataIntegrityViolationException.class)
                .extracting(ex -> ((DataIntegrityViolationException) ex).getMostSpecificCause().getMessage())
                .asString()
                .contains("chk_career_project_end_date_after_start_date");
    }

    /**
     * Requirement 21: a project's dates falling entirely outside its owning position's dates is
     * NOT rejected by the database - that cross-row invariant is explicitly deferred to a later
     * domain/application validation step (see V19's header comment and {@code
     * CareerProjectEntity}'s javadoc), so this is accepted here on purpose.
     */
    @Test
    void projectDatesOutsidePositionDates_areNotRejectedByTheDatabase_laterDomainInvariant() {
        CareerPositionEntity position = position(
                aCompany("project-outside-position-dates-" + UUID.randomUUID()), "Demo Backend Engineer", 0,
                LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1), false);
        careerPositionRepository.saveAndFlush(position);
        CareerProjectEntity outsideDates = project(position, "Billing Platform", 0,
                LocalDate.of(2030, 1, 1), LocalDate.of(2031, 1, 1));

        careerProjectRepository.save(outsideDates);
        entityManager.flush();
        entityManager.clear();

        assertThat(careerProjectRepository.findAllByCareerPositionIdOrderByDisplayOrderAsc(position.getId())).hasSize(1);
    }

    // ==================== 22-25. Bullet constraints ====================

    @Test
    void blankPositionResponsibility_violatesCheckConstraint() {
        CareerPositionEntity position = aPosition("blank-pos-responsibility-" + UUID.randomUUID());

        assertThatThrownBy(() -> careerPositionResponsibilityRepository.saveAndFlush(positionResponsibility(position, "   ", 0)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .extracting(ex -> ((DataIntegrityViolationException) ex).getMostSpecificCause().getMessage())
                .asString()
                .contains("chk_career_position_responsibility_text_not_blank");
    }

    @Test
    void blankPositionAchievement_violatesCheckConstraint() {
        CareerPositionEntity position = aPosition("blank-pos-achievement-" + UUID.randomUUID());

        assertThatThrownBy(() -> careerPositionAchievementRepository.saveAndFlush(positionAchievement(position, "   ", 0)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .extracting(ex -> ((DataIntegrityViolationException) ex).getMostSpecificCause().getMessage())
                .asString()
                .contains("chk_career_position_achievement_text_not_blank");
    }

    @Test
    void blankProjectResponsibility_violatesCheckConstraint() {
        CareerProjectEntity project = aProject("blank-project-responsibility-" + UUID.randomUUID());

        assertThatThrownBy(() -> careerProjectResponsibilityRepository.saveAndFlush(projectResponsibility(project, "   ", 0)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .extracting(ex -> ((DataIntegrityViolationException) ex).getMostSpecificCause().getMessage())
                .asString()
                .contains("chk_career_project_responsibility_text_not_blank");
    }

    @Test
    void blankProjectAchievement_violatesCheckConstraint() {
        CareerProjectEntity project = aProject("blank-project-achievement-" + UUID.randomUUID());

        assertThatThrownBy(() -> careerProjectAchievementRepository.saveAndFlush(projectAchievement(project, "   ", 0)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .extracting(ex -> ((DataIntegrityViolationException) ex).getMostSpecificCause().getMessage())
                .asString()
                .contains("chk_career_project_achievement_text_not_blank");
    }

    @Test
    void duplicateDisplayOrder_forPositionResponsibilities_violatesUniqueConstraint() {
        CareerPositionEntity position = aPosition("dup-pos-resp-order-" + UUID.randomUUID());
        careerPositionResponsibilityRepository.save(positionResponsibility(position, "Own the billing service's reliability", 0));
        entityManager.flush();

        DataIntegrityViolationException exception = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> careerPositionResponsibilityRepository.saveAndFlush(positionResponsibility(position, "On call rotation", 0)));

        assertThat(exception).isNotNull();
        assertThat(exception.getMostSpecificCause().getMessage())
                .contains("uk_career_position_responsibility_position_id_display_order");
    }

    @Test
    void duplicateDisplayOrder_forPositionAchievements_violatesUniqueConstraint() {
        CareerPositionEntity position = aPosition("dup-pos-ach-order-" + UUID.randomUUID());
        careerPositionAchievementRepository.save(positionAchievement(position, "Reduced synthetic processing latency by 20%", 0));
        entityManager.flush();

        DataIntegrityViolationException exception = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> careerPositionAchievementRepository.saveAndFlush(positionAchievement(position, "Mentored two engineers", 0)));

        assertThat(exception).isNotNull();
        assertThat(exception.getMostSpecificCause().getMessage())
                .contains("uk_career_position_achievement_position_id_display_order");
    }

    @Test
    void duplicateDisplayOrder_forProjectResponsibilities_violatesUniqueConstraint() {
        CareerProjectEntity project = aProject("dup-project-resp-order-" + UUID.randomUUID());
        careerProjectResponsibilityRepository.save(projectResponsibility(project, "Design the event schema", 0));
        entityManager.flush();

        DataIntegrityViolationException exception = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> careerProjectResponsibilityRepository.saveAndFlush(projectResponsibility(project, "Write the migration plan", 0)));

        assertThat(exception).isNotNull();
        assertThat(exception.getMostSpecificCause().getMessage())
                .contains("uk_career_project_responsibility_project_id_display_order");
    }

    @Test
    void duplicateDisplayOrder_forProjectAchievements_violatesUniqueConstraint() {
        CareerProjectEntity project = aProject("dup-project-ach-order-" + UUID.randomUUID());
        careerProjectAchievementRepository.save(projectAchievement(project, "Cut duplicate events to zero", 0));
        entityManager.flush();

        DataIntegrityViolationException exception = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> careerProjectAchievementRepository.saveAndFlush(projectAchievement(project, "Automated the rollout", 0)));

        assertThat(exception).isNotNull();
        assertThat(exception.getMostSpecificCause().getMessage())
                .contains("uk_career_project_achievement_project_id_display_order");
    }

    @Test
    void sameResponsibilityText_underDifferentPositions_bothAccepted() {
        CareerPositionEntity positionA = aPosition("same-text-a-" + UUID.randomUUID());
        CareerPositionEntity positionB = aPosition("same-text-b-" + UUID.randomUUID());

        careerPositionResponsibilityRepository.save(positionResponsibility(positionA, "Own the billing service's reliability", 0));
        careerPositionResponsibilityRepository.save(positionResponsibility(positionB, "Own the billing service's reliability", 0));
        entityManager.flush();

        assertThat(careerPositionResponsibilityRepository.findAllByCareerPositionIdOrderByDisplayOrderAsc(positionA.getId())).hasSize(1);
        assertThat(careerPositionResponsibilityRepository.findAllByCareerPositionIdOrderByDisplayOrderAsc(positionB.getId())).hasSize(1);
    }

    // ==================== 26-29. Technology constraints ====================

    @Test
    void blankTechnologyName_violatesCheckConstraint() {
        CareerProjectEntity project = aProject("blank-tech-" + UUID.randomUUID());

        assertThatThrownBy(() -> careerProjectTechnologyRepository.saveAndFlush(technology(project, "   ", 0)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .extracting(ex -> ((DataIntegrityViolationException) ex).getMostSpecificCause().getMessage())
                .asString()
                .contains("chk_career_project_technology_name_not_blank");
    }

    @Test
    void duplicateTechnologyName_inOneProject_violatesUniqueConstraint() {
        CareerProjectEntity project = aProject("dup-tech-name-" + UUID.randomUUID());
        careerProjectTechnologyRepository.save(technology(project, "Kafka", 0));
        entityManager.flush();

        DataIntegrityViolationException exception = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> careerProjectTechnologyRepository.saveAndFlush(technology(project, "Kafka", 1)));

        assertThat(exception).isNotNull();
        assertThat(exception.getMostSpecificCause().getMessage())
                .contains("uk_career_project_technology_project_id_technology_name");
    }

    @Test
    void duplicateTechnologyDisplayOrder_inOneProject_violatesUniqueConstraint() {
        CareerProjectEntity project = aProject("dup-tech-order-" + UUID.randomUUID());
        careerProjectTechnologyRepository.save(technology(project, "Kafka", 0));
        entityManager.flush();

        DataIntegrityViolationException exception = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> careerProjectTechnologyRepository.saveAndFlush(technology(project, "PostgreSQL", 0)));

        assertThat(exception).isNotNull();
        assertThat(exception.getMostSpecificCause().getMessage())
                .contains("uk_career_project_technology_project_id_display_order");
    }

    @Test
    void sameTechnology_inDifferentProjects_bothAccepted() {
        CareerProjectEntity projectA = aProject("tech-project-a-" + UUID.randomUUID());
        CareerProjectEntity projectB = aProject("tech-project-b-" + UUID.randomUUID());

        careerProjectTechnologyRepository.save(technology(projectA, "PostgreSQL", 0));
        careerProjectTechnologyRepository.save(technology(projectB, "PostgreSQL", 0));
        entityManager.flush();

        assertThat(careerProjectTechnologyRepository.findAllByCareerProjectIdOrderByDisplayOrderAsc(projectA.getId())).hasSize(1);
        assertThat(careerProjectTechnologyRepository.findAllByCareerProjectIdOrderByDisplayOrderAsc(projectB.getId())).hasSize(1);
    }

    @Test
    void technologyCapitalization_isPreservedExactly() {
        CareerProjectEntity project = aProject("tech-capitalization-" + UUID.randomUUID());
        careerProjectTechnologyRepository.save(technology(project, "PostgreSQL", 0));
        careerProjectTechnologyRepository.save(technology(project, "AWS", 1));
        careerProjectTechnologyRepository.save(technology(project, "Kubernetes", 2));
        entityManager.flush();
        entityManager.clear();

        assertThat(careerProjectTechnologyRepository.findAllByCareerProjectIdOrderByDisplayOrderAsc(project.getId()))
                .extracting(CareerProjectTechnologyEntity::getTechnologyName)
                .containsExactly("PostgreSQL", "AWS", "Kubernetes");
    }

    // ==================== 30. Optimistic locking ====================

    /**
     * Proves only {@link CareerHistoryEntity#getVersion()} itself, via a direct root update - a
     * child-only change (e.g. adding a responsibility) does NOT increment this version at this
     * persistence-foundation step, since no aggregate adapter exists yet to make that guarantee
     * (see {@link CareerHistoryEntity}'s javadoc). That aggregate-level behavior, following {@code
     * CandidateProfileRepositoryAdapter}'s pattern, is Step 6 work.
     *
     * <p>Unlike {@code CandidateProfileRepositoryTest}'s equivalent test, this root has no other
     * mutable business field to change to force a real UPDATE - a plain {@code save()} of an
     * unmodified detached copy is a documented Hibernate no-op (no dirty state, so no UPDATE and
     * no version check at all), which would make this test pass for the wrong reason (no write
     * ever attempted) rather than prove anything about optimistic locking. {@code
     * LockModeType.OPTIMISTIC_FORCE_INCREMENT} forces the real version-checked UPDATE this test
     * needs. Also unlike that test, the failure is asserted as the raw {@code
     * jakarta.persistence.OptimisticLockException}, not Spring's {@link
     * ObjectOptimisticLockingFailureException}: Spring's exception translation only wraps
     * exceptions thrown from a call into a {@code @Repository} bean's own proxied method, and this
     * call goes through {@link EntityManager#lock} directly.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void staleConcurrentRootUpdate_secondWriterFailsWithOptimisticLockingException() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID historyId = tx.execute(status -> careerHistoryRepository
                .save(careerHistory(candidateProfileRepository.save(profileEntity("opt-lock-" + UUID.randomUUID()))))
                .getId());

        CareerHistoryEntity firstWriterCopy = tx.execute(status -> careerHistoryRepository.findById(historyId).orElseThrow());
        CareerHistoryEntity secondWriterCopy = tx.execute(status -> careerHistoryRepository.findById(historyId).orElseThrow());

        tx.execute(status -> {
            forceVersionIncrement(firstWriterCopy);
            return null;
        });

        assertThatThrownBy(() -> tx.execute(status -> {
                    forceVersionIncrement(secondWriterCopy);
                    return null;
                }))
                .isInstanceOf(jakarta.persistence.OptimisticLockException.class);

        CareerHistoryEntity finalState = tx.execute(status -> careerHistoryRepository.findById(historyId).orElseThrow());
        assertThat(finalState.getVersion()).isEqualTo(1L);
    }

    private void forceVersionIncrement(CareerHistoryEntity detachedCopy) {
        CareerHistoryEntity managed = entityManager.merge(detachedCopy);
        entityManager.lock(managed, jakarta.persistence.LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }

    // ==================== 31-35. Cascades ====================

    @Test
    void deletingProject_cascadesToItsResponsibilitiesAchievementsAndTechnologies() {
        CareerProjectEntity project = aProject("cascade-project-" + UUID.randomUUID());
        careerProjectResponsibilityRepository.save(projectResponsibility(project, "Design the event schema", 0));
        careerProjectAchievementRepository.save(projectAchievement(project, "Cut duplicate events to zero", 0));
        careerProjectTechnologyRepository.save(technology(project, "Kafka", 0));
        entityManager.flush();
        UUID projectId = project.getId();

        entityManager.clear();
        careerProjectRepository.delete(careerProjectRepository.findById(projectId).orElseThrow());
        entityManager.flush();
        entityManager.clear();

        assertThat(countRows("career_project_responsibility", "career_project_id", projectId)).isZero();
        assertThat(countRows("career_project_achievement", "career_project_id", projectId)).isZero();
        assertThat(countRows("career_project_technology", "career_project_id", projectId)).isZero();
    }

    @Test
    void deletingPosition_cascadesToResponsibilitiesAchievementsProjectsAndProjectChildren() {
        CareerPositionEntity position = aPosition("cascade-position-" + UUID.randomUUID());
        careerPositionResponsibilityRepository.save(positionResponsibility(position, "Own the billing service's reliability", 0));
        careerPositionAchievementRepository.save(positionAchievement(position, "Reduced synthetic processing latency by 20%", 0));
        CareerProjectEntity project = careerProjectRepository.save(project(position, "Billing Platform", 0, null, null));
        careerProjectTechnologyRepository.save(technology(project, "Kafka", 0));
        entityManager.flush();
        UUID positionId = position.getId();
        UUID projectId = project.getId();

        entityManager.clear();
        careerPositionRepository.delete(careerPositionRepository.findById(positionId).orElseThrow());
        entityManager.flush();
        entityManager.clear();

        assertThat(countRows("career_position_responsibility", "career_position_id", positionId)).isZero();
        assertThat(countRows("career_position_achievement", "career_position_id", positionId)).isZero();
        assertThat(countRows("career_project", "career_position_id", positionId)).isZero();
        assertThat(countRows("career_project_technology", "career_project_id", projectId)).isZero();
    }

    @Test
    void deletingCompany_cascadesToAllPositionsAndDescendants() {
        CareerCompanyEntity company = aCompany("cascade-company-" + UUID.randomUUID());
        CareerPositionEntity position = careerPositionRepository.save(
                position(company, "Demo Backend Engineer", 0, LocalDate.of(2020, 1, 1), null, true));
        careerPositionResponsibilityRepository.save(positionResponsibility(position, "Own the billing service's reliability", 0));
        entityManager.flush();
        UUID companyId = company.getId();
        UUID positionId = position.getId();

        entityManager.clear();
        careerCompanyRepository.delete(careerCompanyRepository.findById(companyId).orElseThrow());
        entityManager.flush();
        entityManager.clear();

        assertThat(countRows("career_position", "career_company_id", companyId)).isZero();
        assertThat(countRows("career_position_responsibility", "career_position_id", positionId)).isZero();
    }

    @Test
    void deletingCareerHistory_cascadesToItsCompleteGraph() {
        CandidateProfileEntity profile = candidateProfile("cascade-history-" + UUID.randomUUID());
        CareerHistoryEntity history = careerHistoryRepository.save(careerHistory(profile));
        CareerCompanyEntity company = careerCompanyRepository.save(company(history, "Example Systems", 0));
        careerPositionRepository.save(position(company, "Demo Backend Engineer", 0, LocalDate.of(2020, 1, 1), null, true));
        entityManager.flush();
        UUID historyId = history.getId();
        UUID companyId = company.getId();

        entityManager.clear();
        careerHistoryRepository.delete(careerHistoryRepository.findById(historyId).orElseThrow());
        entityManager.flush();
        entityManager.clear();

        assertThat(countRows("career_company", "career_history_id", historyId)).isZero();
        assertThat(countRows("career_position", "career_company_id", companyId)).isZero();
        assertThat(candidateProfileRepository.findById(profile.getId())).isPresent();
    }

    @Test
    void deletingCandidateProfile_cascadesToCareerHistoryAndItsCompleteGraph() {
        CandidateProfileEntity profile = candidateProfile("cascade-profile-" + UUID.randomUUID());
        CareerHistoryEntity history = careerHistoryRepository.save(careerHistory(profile));
        CareerCompanyEntity company = careerCompanyRepository.save(company(history, "Example Systems", 0));
        entityManager.flush();
        UUID profileId = profile.getId();
        UUID historyId = history.getId();

        entityManager.clear();
        candidateProfileRepository.delete(candidateProfileRepository.findById(profileId).orElseThrow());
        entityManager.flush();
        entityManager.clear();

        assertThat(countRows("career_history", "candidate_profile_id", profileId)).isZero();
        assertThat(countRows("career_company", "career_history_id", historyId)).isZero();
    }

    // ==================== Helpers ====================

    private CareerPositionEntity aPosition(String profileKeySuffix) {
        CareerCompanyEntity company = aCompany(profileKeySuffix);
        return careerPositionRepository.save(position(company, "Demo Backend Engineer", 0, LocalDate.of(2020, 1, 1), null, true));
    }

    private CareerCompanyEntity aCompany(String profileKeySuffix) {
        CareerHistoryEntity history = careerHistoryRepository.save(careerHistory(candidateProfile(profileKeySuffix)));
        return careerCompanyRepository.save(company(history, "Example Systems", 0));
    }

    private CareerProjectEntity aProject(String profileKeySuffix) {
        CareerPositionEntity position = aPosition(profileKeySuffix);
        return careerProjectRepository.save(project(position, "Billing Platform", 0, null, null));
    }

    private long countRows(String table, String fkColumn, UUID id) {
        return ((Number) entityManager
                        .createNativeQuery("SELECT COUNT(*) FROM " + table + " WHERE " + fkColumn + " = ?1")
                        .setParameter(1, id)
                        .getSingleResult())
                .longValue();
    }

    private CandidateProfileEntity candidateProfile(String profileKey) {
        return candidateProfileRepository.save(profileEntity(profileKey));
    }

    private CandidateProfileEntity profileEntity(String profileKey) {
        return CandidateProfileEntity.builder()
                .profileKey(profileKey)
                .targetRole("Demo Backend Engineer")
                .seniority("Senior")
                .experienceYears(5)
                .build();
    }

    private CareerHistoryEntity careerHistory(CandidateProfileEntity profile) {
        return CareerHistoryEntity.builder().candidateProfile(profile).build();
    }

    private CareerCompanyEntity company(CareerHistoryEntity history, String name, int displayOrder) {
        return CareerCompanyEntity.builder().careerHistory(history).name(name).displayOrder(displayOrder).build();
    }

    private CareerPositionEntity position(
            CareerCompanyEntity company, String title, int displayOrder, LocalDate startDate, LocalDate endDate, boolean currentRole) {
        return CareerPositionEntity.builder()
                .careerCompany(company)
                .title(title)
                .startDate(startDate)
                .endDate(endDate)
                .currentRole(currentRole)
                .displayOrder(displayOrder)
                .build();
    }

    private CareerPositionResponsibilityEntity positionResponsibility(CareerPositionEntity position, String text, int displayOrder) {
        return CareerPositionResponsibilityEntity.builder()
                .careerPosition(position)
                .responsibilityText(text)
                .displayOrder(displayOrder)
                .build();
    }

    private CareerPositionAchievementEntity positionAchievement(CareerPositionEntity position, String text, int displayOrder) {
        return CareerPositionAchievementEntity.builder()
                .careerPosition(position)
                .achievementText(text)
                .displayOrder(displayOrder)
                .build();
    }

    private CareerProjectEntity project(CareerPositionEntity position, String name, int displayOrder, LocalDate startDate, LocalDate endDate) {
        return CareerProjectEntity.builder()
                .careerPosition(position)
                .name(name)
                .startDate(startDate)
                .endDate(endDate)
                .displayOrder(displayOrder)
                .build();
    }

    private CareerProjectResponsibilityEntity projectResponsibility(CareerProjectEntity project, String text, int displayOrder) {
        return CareerProjectResponsibilityEntity.builder()
                .careerProject(project)
                .responsibilityText(text)
                .displayOrder(displayOrder)
                .build();
    }

    private CareerProjectAchievementEntity projectAchievement(CareerProjectEntity project, String text, int displayOrder) {
        return CareerProjectAchievementEntity.builder()
                .careerProject(project)
                .achievementText(text)
                .displayOrder(displayOrder)
                .build();
    }

    private CareerProjectTechnologyEntity technology(CareerProjectEntity project, String name, int displayOrder) {
        return CareerProjectTechnologyEntity.builder()
                .careerProject(project)
                .technologyName(name)
                .displayOrder(displayOrder)
                .build();
    }
}

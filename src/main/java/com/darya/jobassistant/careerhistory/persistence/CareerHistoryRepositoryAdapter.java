package com.darya.jobassistant.careerhistory.persistence;

import com.darya.jobassistant.candidates.entity.CandidateProfileEntity;
import com.darya.jobassistant.candidates.repository.CandidateProfileRepository;
import com.darya.jobassistant.careerhistory.aggregate.CareerAchievement;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAlreadyExistsException;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryCandidateProfileNotFoundException;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryConcurrentModificationException;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryRepositoryPort;
import com.darya.jobassistant.careerhistory.aggregate.CareerPosition;
import com.darya.jobassistant.careerhistory.aggregate.CareerProject;
import com.darya.jobassistant.careerhistory.aggregate.CareerResponsibility;
import com.darya.jobassistant.careerhistory.aggregate.CareerTechnology;
import com.darya.jobassistant.careerhistory.entity.CareerCompanyEntity;
import com.darya.jobassistant.careerhistory.entity.CareerHistoryEntity;
import com.darya.jobassistant.careerhistory.entity.CareerPositionAchievementEntity;
import com.darya.jobassistant.careerhistory.entity.CareerPositionEntity;
import com.darya.jobassistant.careerhistory.entity.CareerPositionResponsibilityEntity;
import com.darya.jobassistant.careerhistory.entity.CareerProjectAchievementEntity;
import com.darya.jobassistant.careerhistory.entity.CareerProjectEntity;
import com.darya.jobassistant.careerhistory.entity.CareerProjectResponsibilityEntity;
import com.darya.jobassistant.careerhistory.entity.CareerProjectTechnologyEntity;
import com.darya.jobassistant.careerhistory.repository.CareerCompanyRepository;
import com.darya.jobassistant.careerhistory.repository.CareerHistoryRepository;
import com.darya.jobassistant.careerhistory.repository.CareerPositionAchievementRepository;
import com.darya.jobassistant.careerhistory.repository.CareerPositionRepository;
import com.darya.jobassistant.careerhistory.repository.CareerPositionResponsibilityRepository;
import com.darya.jobassistant.careerhistory.repository.CareerProjectAchievementRepository;
import com.darya.jobassistant.careerhistory.repository.CareerProjectRepository;
import com.darya.jobassistant.careerhistory.repository.CareerProjectResponsibilityRepository;
import com.darya.jobassistant.careerhistory.repository.CareerProjectTechnologyRepository;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL/JPA adapter for {@link CareerHistoryRepositoryPort} - Sprint 9 Step 6. Not wired into
 * any runtime workflow yet: Career History remains entirely optional, and {@code
 * PersistentCandidateProfileProvider}/{@code JobAnalysisService} are unaffected.
 *
 * <p>Depends on all nine internal Career History Spring Data repositories (Step 5) plus {@link
 * CandidateProfileRepository} (for the create-time "does this candidate profile exist" check) -
 * matching {@link com.darya.jobassistant.candidates.persistence.CandidateProfileRepositoryAdapter}'s
 * "one adapter per aggregate, built from its own table-level repositories" convention.
 *
 * <h2>Loading strategy - level-based, bounded query count</h2>
 *
 * {@link #findByCandidateProfileId} issues exactly one query per graph level (root, companies,
 * positions, position responsibilities, position achievements, projects, project responsibilities,
 * project achievements, technologies - nine total) via the {@code findAllBy...IdIn...} repository
 * methods added in this step, never one query per company/position/project. The result is
 * assembled in memory by {@link CareerHistoryPersistenceMapper#toDomain}. An empty level (e.g. no
 * companies at all) short-circuits every deeper query to an empty list rather than issuing an
 * {@code IN ()} query for nothing.
 *
 * <h2>Save strategy - atomic full-graph replacement</h2>
 *
 * {@link #save} always treats the supplied aggregate as the complete desired state: the root is
 * created or version-checked first (a stale version fails here, before any child row is touched),
 * then the entire existing company graph is deleted (database {@code ON DELETE CASCADE}, V19,
 * removes every descendant) and flushed before the entire supplied graph is inserted level by
 * level, so a child row is never inserted before its parent exists. Supplied non-null child ids
 * are preserved; new children (null id) receive a generated one. Everything happens inside the one
 * {@code @Transactional} method - a failure at any point rolls back the root version change together
 * with every graph mutation already attempted.
 */
@Repository
@RequiredArgsConstructor
public class CareerHistoryRepositoryAdapter implements CareerHistoryRepositoryPort {

    /** Must match the constraint name in {@code V19__create_career_history.sql}. */
    private static final String CANDIDATE_PROFILE_UNIQUE_INDEX_NAME = "uk_career_history_candidate_profile_id";

    private final CareerHistoryRepository careerHistoryRepository;
    private final CareerCompanyRepository careerCompanyRepository;
    private final CareerPositionRepository careerPositionRepository;
    private final CareerPositionResponsibilityRepository careerPositionResponsibilityRepository;
    private final CareerPositionAchievementRepository careerPositionAchievementRepository;
    private final CareerProjectRepository careerProjectRepository;
    private final CareerProjectResponsibilityRepository careerProjectResponsibilityRepository;
    private final CareerProjectAchievementRepository careerProjectAchievementRepository;
    private final CareerProjectTechnologyRepository careerProjectTechnologyRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final Clock clock;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public Optional<CareerHistoryAggregate> findByCandidateProfileId(UUID candidateProfileId) {
        return careerHistoryRepository.findByCandidateProfileId(candidateProfileId).map(this::loadComplete);
    }

    /**
     * Creates or version-checks the root, then unconditionally replaces the entire child graph -
     * see the class javadoc for the exact ordering guarantees. A create for a {@code
     * candidateProfileId} that already has a Career History, or one referencing a nonexistent
     * Candidate Profile, is rejected before any child row is written.
     */
    @Override
    @Transactional
    public CareerHistoryAggregate save(CareerHistoryAggregate careerHistory) {
        CareerHistoryEntity historyEntity = careerHistory.id() == null ? createRoot(careerHistory) : updateRoot(careerHistory);
        UUID historyId = historyEntity.getId();
        deleteExistingGraph(historyId);
        insertGraph(historyId, careerHistory.companies());
        // The graph above was inserted via native SQL (see insertGraph's javadoc), entirely
        // outside the persistence context - clearing it before the final reload guarantees
        // loadComplete's queries observe the true committed-so-far database state, never stale
        // first-level-cache state left over from earlier in this transaction.
        entityManager.flush();
        entityManager.clear();
        CareerHistoryEntity reloadedRoot = careerHistoryRepository.findById(historyId).orElseThrow(() -> concurrentModification(careerHistory));
        return loadComplete(reloadedRoot);
    }

    private CareerHistoryEntity createRoot(CareerHistoryAggregate careerHistory) {
        CandidateProfileEntity candidateProfile = candidateProfileRepository.findById(careerHistory.candidateProfileId())
                .orElseThrow(() -> new CareerHistoryCandidateProfileNotFoundException(careerHistory.candidateProfileId()));
        try {
            return careerHistoryRepository.saveAndFlush(
                    CareerHistoryPersistenceMapper.toNewRootEntity(careerHistory, candidateProfile));
        } catch (DataIntegrityViolationException e) {
            if (isCandidateProfileConflict(e)) {
                throw new CareerHistoryAlreadyExistsException(careerHistory.candidateProfileId());
            }
            throw e;
        }
    }

    /** PostgreSQL's SQLSTATE for {@code serialization_failure} - see {@link #isSerializationFailure}. */
    private static final String SERIALIZATION_FAILURE_SQLSTATE = "40001";

    /**
     * Explicit, unconditional version-checked write of the root row - always via {@link
     * CareerHistoryRepository#updateVersionIfMatches}, never Hibernate's own dirty checking. A
     * stale {@code expectedVersion} fails here, before {@link #deleteExistingGraph} ever runs.
     *
     * <p>Sprint 9 Step 7 correction: two transactions racing to update the exact same row at the
     * exact same starting version do not always resolve as "0 rows updated" - under {@code
     * REPEATABLE_READ}, PostgreSQL itself can reject the second transaction's {@code UPDATE}
     * outright with a {@code 40001 serialization_failure} ("could not serialize access due to
     * concurrent update") once the first transaction commits its own conflicting change to that
     * row first. This is exactly the same "another transaction changed this row first" condition
     * the {@code updatedRows == 0} branch already reports, so it is translated identically -
     * <em>only</em> when the SQLSTATE is confirmed to be {@code 40001}.
     *
     * <p>The translation is deliberately narrow and SQLSTATE-based, not exception-type-based:
     * against this exact stack (Hibernate 6.6 + this project's pinned PostgreSQL driver), Hibernate's
     * own {@code SQLStateConversionDelegate} maps the {@code 40001} serialization failure to
     * {@code org.hibernate.exception.LockAcquisitionException}, which Spring's {@code
     * HibernateJpaDialect} in turn converts to {@link org.springframework.dao.CannotAcquireLockException}
     * - the exact same Spring exception <em>class</em> a genuine PostgreSQL lock-timeout/
     * lock-not-available failure (SQLSTATE {@code 55P03}) would also surface as. Catching by
     * exception type alone would therefore either miss the real serialization race or
     * misclassify an unrelated lock timeout as one; only the underlying SQLSTATE, read from the
     * deepest {@link SQLException} via {@link DataAccessException#getMostSpecificCause()} (the
     * same idiom {@link #isCandidateProfileConflict} already uses for the analogous constraint-name
     * check), reliably distinguishes them. Never matched by exception message text.
     */
    private CareerHistoryEntity updateRoot(CareerHistoryAggregate careerHistory) {
        int updatedRows;
        try {
            updatedRows = careerHistoryRepository.updateVersionIfMatches(
                    careerHistory.id(), careerHistory.candidateProfileId(), Instant.now(clock), careerHistory.version());
        } catch (DataAccessException e) {
            if (!isSerializationFailure(e)) {
                throw e;
            }
            throw concurrentModification(careerHistory);
        }
        if (updatedRows == 0) {
            throw concurrentModification(careerHistory);
        }
        return careerHistoryRepository.findById(careerHistory.id()).orElseThrow(() -> concurrentModification(careerHistory));
    }

    /**
     * {@code true} only when {@code exception}'s deepest cause is a {@link SQLException} whose
     * {@link SQLException#getSQLState()} is exactly {@value #SERIALIZATION_FAILURE_SQLSTATE} - a
     * lock timeout ({@code 55P03}), a deadlock ({@code 40P01}), or any {@link DataAccessException}
     * with no {@link SQLException} cause at all (e.g. a connection-level failure) all return
     * {@code false} here and are left to propagate unchanged from {@link #updateRoot}.
     */
    private boolean isSerializationFailure(DataAccessException exception) {
        Throwable mostSpecificCause = exception.getMostSpecificCause();
        return mostSpecificCause instanceof SQLException sqlException
                && SERIALIZATION_FAILURE_SQLSTATE.equals(sqlException.getSQLState());
    }

    /**
     * Deletes only the companies belonging to {@code historyId} (and, via {@code ON DELETE
     * CASCADE}, their complete descendant graph) - {@link CareerCompanyRepository#deleteAllByCareerHistoryId}
     * is an explicit {@code WHERE career_history_id = :historyId} bulk delete, never the
     * table-wide, no-argument {@code deleteAll()} - replacing one Career History's graph can never
     * touch another's rows (see {@code CareerHistoryRepositoryAdapterIsolationTest}). A no-op for
     * a brand-new root, which has no existing companies yet.
     *
     * <p>{@code flushAutomatically = true} on that query is required, not cosmetic: without it,
     * Hibernate's default flush ordering could execute {@link #insertGraph}'s native inserts
     * before this delete actually reaches the database, self-colliding on the unique {@code
     * (parent_id, display_order)}/{@code (parent_id, name)} constraints for any value reused
     * across the replace. {@code clearAutomatically = true} drops the now-stale first-level-cache
     * entries for the rows just deleted.
     */
    private void deleteExistingGraph(UUID historyId) {
        careerCompanyRepository.deleteAllByCareerHistoryId(historyId);
    }

    /**
     * Inserts the complete supplied graph, parent before child throughout (a plain nested
     * traversal - company, then each of its positions, then each position's bullets/projects,
     * then each project's bullets/technologies - already guarantees this, since every row here
     * needs only its own already-inserted parent id, not any batch-level ordering).
     *
     * <p>Uses hand-written native {@code INSERT} statements ({@link #insertCompany} etc.), not
     * {@code repository.saveAll}/{@link EntityManager#persist} - {@link org.hibernate.id.uuid.UuidGenerator},
     * which backs {@code @GeneratedValue(strategy = GenerationType.UUID)} on every entity in this
     * package (via {@code BaseEntity}), unconditionally generates a fresh value and ignores
     * whatever the entity's id field already holds (confirmed against Hibernate 6.6's own source -
     * {@code UuidGenerator.generate} never reads its {@code currentValue} parameter) - there is no
     * supported JPA/Hibernate entity-lifecycle operation that both goes through this generator and
     * honors a preserved id. Every id used here is therefore decided in application code first
     * ({@link #idOrNew}: the caller's preserved id, or a freshly generated one) and written
     * directly, bypassing the generator entirely - which also means {@code created_at}/{@code
     * updated_at} bypass {@code @CreatedDate}/{@code @LastModifiedDate} auditing and are set
     * explicitly here instead.
     */
    private void insertGraph(UUID historyId, List<CareerCompany> companies) {
        Instant now = Instant.now(clock);
        for (CareerCompany company : companies) {
            UUID companyId = idOrNew(company.id());
            insertCompany(companyId, historyId, company, now);
            for (CareerPosition position : company.positions()) {
                UUID positionId = idOrNew(position.id());
                insertPosition(positionId, companyId, position, now);
                for (CareerResponsibility responsibility : position.responsibilities()) {
                    insertPositionResponsibility(idOrNew(responsibility.id()), positionId, responsibility, now);
                }
                for (CareerAchievement achievement : position.achievements()) {
                    insertPositionAchievement(idOrNew(achievement.id()), positionId, achievement, now);
                }
                for (CareerProject project : position.projects()) {
                    UUID projectId = idOrNew(project.id());
                    insertProject(projectId, positionId, project, now);
                    for (CareerResponsibility responsibility : project.responsibilities()) {
                        insertProjectResponsibility(idOrNew(responsibility.id()), projectId, responsibility, now);
                    }
                    for (CareerAchievement achievement : project.achievements()) {
                        insertProjectAchievement(idOrNew(achievement.id()), projectId, achievement, now);
                    }
                    for (CareerTechnology technology : project.technologies()) {
                        insertTechnology(idOrNew(technology.id()), projectId, technology, now);
                    }
                }
            }
        }
    }

    private UUID idOrNew(UUID id) {
        return id != null ? id : UUID.randomUUID();
    }

    private void insertCompany(UUID id, UUID historyId, CareerCompany company, Instant now) {
        entityManager.createNativeQuery("""
                        INSERT INTO career_company
                            (id, career_history_id, name, website, industry, location, description, display_order, created_at, updated_at)
                        VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)
                        """)
                .setParameter(1, id)
                .setParameter(2, historyId)
                .setParameter(3, company.name())
                .setParameter(4, company.website())
                .setParameter(5, company.industry())
                .setParameter(6, company.location())
                .setParameter(7, company.description())
                .setParameter(8, company.displayOrder())
                .setParameter(9, now)
                .setParameter(10, now)
                .executeUpdate();
    }

    private void insertPosition(UUID id, UUID companyId, CareerPosition position, Instant now) {
        entityManager.createNativeQuery("""
                        INSERT INTO career_position
                            (id, career_company_id, title, employment_type, location, work_arrangement, start_date, end_date,
                             is_current_role, description, display_order, created_at, updated_at)
                        VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13)
                        """)
                .setParameter(1, id)
                .setParameter(2, companyId)
                .setParameter(3, position.title())
                .setParameter(4, position.employmentType())
                .setParameter(5, position.location())
                .setParameter(6, position.workArrangement())
                .setParameter(7, position.startDate())
                .setParameter(8, position.endDate())
                .setParameter(9, position.currentRole())
                .setParameter(10, position.description())
                .setParameter(11, position.displayOrder())
                .setParameter(12, now)
                .setParameter(13, now)
                .executeUpdate();
    }

    private void insertPositionResponsibility(UUID id, UUID positionId, CareerResponsibility responsibility, Instant now) {
        entityManager.createNativeQuery("""
                        INSERT INTO career_position_responsibility (id, career_position_id, responsibility_text, display_order, created_at, updated_at)
                        VALUES (?1, ?2, ?3, ?4, ?5, ?6)
                        """)
                .setParameter(1, id)
                .setParameter(2, positionId)
                .setParameter(3, responsibility.text())
                .setParameter(4, responsibility.displayOrder())
                .setParameter(5, now)
                .setParameter(6, now)
                .executeUpdate();
    }

    private void insertPositionAchievement(UUID id, UUID positionId, CareerAchievement achievement, Instant now) {
        entityManager.createNativeQuery("""
                        INSERT INTO career_position_achievement (id, career_position_id, achievement_text, display_order, created_at, updated_at)
                        VALUES (?1, ?2, ?3, ?4, ?5, ?6)
                        """)
                .setParameter(1, id)
                .setParameter(2, positionId)
                .setParameter(3, achievement.text())
                .setParameter(4, achievement.displayOrder())
                .setParameter(5, now)
                .setParameter(6, now)
                .executeUpdate();
    }

    private void insertProject(UUID id, UUID positionId, CareerProject project, Instant now) {
        entityManager.createNativeQuery("""
                        INSERT INTO career_project (id, career_position_id, name, description, start_date, end_date, display_order, created_at, updated_at)
                        VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)
                        """)
                .setParameter(1, id)
                .setParameter(2, positionId)
                .setParameter(3, project.name())
                .setParameter(4, project.description())
                .setParameter(5, project.startDate())
                .setParameter(6, project.endDate())
                .setParameter(7, project.displayOrder())
                .setParameter(8, now)
                .setParameter(9, now)
                .executeUpdate();
    }

    private void insertProjectResponsibility(UUID id, UUID projectId, CareerResponsibility responsibility, Instant now) {
        entityManager.createNativeQuery("""
                        INSERT INTO career_project_responsibility (id, career_project_id, responsibility_text, display_order, created_at, updated_at)
                        VALUES (?1, ?2, ?3, ?4, ?5, ?6)
                        """)
                .setParameter(1, id)
                .setParameter(2, projectId)
                .setParameter(3, responsibility.text())
                .setParameter(4, responsibility.displayOrder())
                .setParameter(5, now)
                .setParameter(6, now)
                .executeUpdate();
    }

    private void insertProjectAchievement(UUID id, UUID projectId, CareerAchievement achievement, Instant now) {
        entityManager.createNativeQuery("""
                        INSERT INTO career_project_achievement (id, career_project_id, achievement_text, display_order, created_at, updated_at)
                        VALUES (?1, ?2, ?3, ?4, ?5, ?6)
                        """)
                .setParameter(1, id)
                .setParameter(2, projectId)
                .setParameter(3, achievement.text())
                .setParameter(4, achievement.displayOrder())
                .setParameter(5, now)
                .setParameter(6, now)
                .executeUpdate();
    }

    private void insertTechnology(UUID id, UUID projectId, CareerTechnology technology, Instant now) {
        entityManager.createNativeQuery("""
                        INSERT INTO career_project_technology (id, career_project_id, technology_name, category, display_order, created_at, updated_at)
                        VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)
                        """)
                .setParameter(1, id)
                .setParameter(2, projectId)
                .setParameter(3, technology.name())
                .setParameter(4, technology.category())
                .setParameter(5, technology.displayOrder())
                .setParameter(6, now)
                .setParameter(7, now)
                .executeUpdate();
    }

    private CareerHistoryAggregate loadComplete(CareerHistoryEntity historyEntity) {
        List<CareerCompanyEntity> companies = careerCompanyRepository.findAllByCareerHistoryIdOrderByDisplayOrderAsc(historyEntity.getId());
        List<UUID> companyIds = idsOf(companies, CareerCompanyEntity::getId);

        List<CareerPositionEntity> positions = companyIds.isEmpty() ? List.of()
                : careerPositionRepository.findAllByCareerCompanyIdInOrderByDisplayOrderAsc(companyIds);
        List<UUID> positionIds = idsOf(positions, CareerPositionEntity::getId);

        List<CareerPositionResponsibilityEntity> positionResponsibilities = positionIds.isEmpty() ? List.of()
                : careerPositionResponsibilityRepository.findAllByCareerPositionIdInOrderByDisplayOrderAsc(positionIds);
        List<CareerPositionAchievementEntity> positionAchievements = positionIds.isEmpty() ? List.of()
                : careerPositionAchievementRepository.findAllByCareerPositionIdInOrderByDisplayOrderAsc(positionIds);
        List<CareerProjectEntity> projects = positionIds.isEmpty() ? List.of()
                : careerProjectRepository.findAllByCareerPositionIdInOrderByDisplayOrderAsc(positionIds);
        List<UUID> projectIds = idsOf(projects, CareerProjectEntity::getId);

        List<CareerProjectResponsibilityEntity> projectResponsibilities = projectIds.isEmpty() ? List.of()
                : careerProjectResponsibilityRepository.findAllByCareerProjectIdInOrderByDisplayOrderAsc(projectIds);
        List<CareerProjectAchievementEntity> projectAchievements = projectIds.isEmpty() ? List.of()
                : careerProjectAchievementRepository.findAllByCareerProjectIdInOrderByDisplayOrderAsc(projectIds);
        List<CareerProjectTechnologyEntity> technologies = projectIds.isEmpty() ? List.of()
                : careerProjectTechnologyRepository.findAllByCareerProjectIdInOrderByDisplayOrderAsc(projectIds);

        return CareerHistoryPersistenceMapper.toDomain(
                historyEntity, companies, positions, positionResponsibilities, positionAchievements,
                projects, projectResponsibilities, projectAchievements, technologies);
    }

    private <E> List<UUID> idsOf(List<E> entities, Function<E, UUID> idExtractor) {
        return entities.stream().map(idExtractor).toList();
    }

    private CareerHistoryConcurrentModificationException concurrentModification(CareerHistoryAggregate careerHistory) {
        return new CareerHistoryConcurrentModificationException(
                careerHistory.id(), careerHistory.candidateProfileId(), careerHistory.version());
    }

    /**
     * Prefers Hibernate's own structural constraint-name extraction, falling back to matching the
     * constraint name in the deepest {@link java.sql.SQLException}'s message - the same two-tier
     * strategy {@code CandidateProfileMigrationUseCase#isProfileKeyConflict} uses for its analogous
     * race, so a differently-named constraint violation is confidently rethrown rather than
     * misclassified as this specific one.
     */
    private boolean isCandidateProfileConflict(DataIntegrityViolationException e) {
        ConstraintViolationException constraintViolation = findConstraintViolationException(e);
        if (constraintViolation != null && constraintViolation.getConstraintName() != null) {
            return CANDIDATE_PROFILE_UNIQUE_INDEX_NAME.equals(constraintViolation.getConstraintName());
        }
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(CANDIDATE_PROFILE_UNIQUE_INDEX_NAME);
    }

    private ConstraintViolationException findConstraintViolationException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolationException) {
                return constraintViolationException;
            }
            current = current.getCause();
        }
        return null;
    }
}

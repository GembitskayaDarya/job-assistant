package com.darya.jobassistant.candidates.repository;

import com.darya.jobassistant.candidates.entity.CandidateProfileEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Internal Spring Data repository for {@link CandidateProfileEntity} - Sprint 9 Step 1 persistence
 * foundation, plus {@link #updateIfVersionMatches} added in Sprint 9 Step 2's correction. Not yet
 * called by any application service outside {@code candidates.persistence}; the runtime Candidate
 * Profile provider remains {@code ConfigurationCandidateProfileProvider}.
 */
public interface CandidateProfileRepository extends JpaRepository<CandidateProfileEntity, UUID> {

    Optional<CandidateProfileEntity> findByProfileKey(String profileKey);

    boolean existsByProfileKey(String profileKey);

    /**
     * Deliberate, explicit optimistic-locking write for the whole parent aggregate row - always
     * unconditionally increments {@code version} and always checks {@code expectedVersion}
     * against whatever is currently stored, regardless of whether any individual scalar field
     * actually differs from its current value. This is intentionally independent of Hibernate's
     * own scalar-field dirty checking (see {@code CandidateProfileRepositoryAdapter#saveParent}'s
     * javadoc): the Candidate Profile aggregate also includes skills and languages, so a save that
     * changes only those must still be treated as a real modification of this row for
     * optimistic-locking purposes, not a no-op.
     *
     * <p>{@code updatedAt} is set explicitly because a JPQL bulk update bypasses the {@code
     * @LastModifiedDate}/{@code AuditingEntityListener} lifecycle callback entirely - the same
     * reason {@code VacancyRecommendationTaskRepository}'s bulk updates set it themselves.
     *
     * <p>{@code clearAutomatically = true} because a bulk update never touches any already-managed
     * entity in the current persistence context: without clearing it, a caller that (in the same
     * transaction) already holds or later re-loads a {@code CandidateProfileEntity} for this same
     * id would silently see pre-update, first-level-cache-stale state instead of this write's
     * effect - {@link com.darya.jobassistant.candidates.persistence.CandidateProfileRepositoryAdapter#saveParent}
     * always re-fetches by id immediately after calling this method, specifically to get a fresh
     * managed instance rather than a stale cached one.
     *
     * @return 1 if a row with this id and exactly this version existed and was updated, 0
     *     otherwise (the row does not exist, or {@code expectedVersion} is stale) - the caller
     *     must translate 0 to a concurrency failure, never treat it as a silent no-op success
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE CandidateProfileEntity c
            SET c.profileKey = :profileKey,
                c.targetRole = :targetRole,
                c.seniority = :seniority,
                c.experienceYears = :experienceYears,
                c.preferredCompanyType = :preferredCompanyType,
                c.preferredLocation = :preferredLocation,
                c.employmentModel = :employmentModel,
                c.remotePolicy = :remotePolicy,
                c.salaryCurrency = :salaryCurrency,
                c.minimumSalary = :minimumSalary,
                c.currentCountry = :currentCountry,
                c.relocationAllowed = :relocationAllowed,
                c.salaryExpectationNote = :salaryExpectationNote,
                c.email = :email,
                c.phone = :phone,
                c.linkedinUrl = :linkedinUrl,
                c.cvLocation = :cvLocation,
                c.cvHeadline = :cvHeadline,
                c.updatedAt = :updatedAt,
                c.version = c.version + 1
            WHERE c.id = :id AND c.version = :expectedVersion
            """)
    int updateIfVersionMatches(
            @Param("id") UUID id,
            @Param("profileKey") String profileKey,
            @Param("targetRole") String targetRole,
            @Param("seniority") String seniority,
            @Param("experienceYears") int experienceYears,
            @Param("preferredCompanyType") String preferredCompanyType,
            @Param("preferredLocation") String preferredLocation,
            @Param("employmentModel") String employmentModel,
            @Param("remotePolicy") String remotePolicy,
            @Param("salaryCurrency") String salaryCurrency,
            @Param("minimumSalary") BigDecimal minimumSalary,
            @Param("currentCountry") String currentCountry,
            @Param("relocationAllowed") boolean relocationAllowed,
            @Param("salaryExpectationNote") String salaryExpectationNote,
            @Param("email") String email,
            @Param("phone") String phone,
            @Param("linkedinUrl") String linkedinUrl,
            @Param("cvLocation") String cvLocation,
            @Param("cvHeadline") String cvHeadline,
            @Param("updatedAt") Instant updatedAt,
            @Param("expectedVersion") long expectedVersion);
}

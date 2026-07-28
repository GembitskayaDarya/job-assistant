package com.darya.jobassistant.vacancies.repository;

import com.darya.jobassistant.vacancies.dto.VacancyPersistenceResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.url.CanonicalVacancyUrl;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VacancyRepository extends JpaRepository<Vacancy, UUID> {

    List<Vacancy> findByCompanyId(UUID companyId);

    List<Vacancy> findByTitleContainingIgnoreCase(String title);

    Optional<Vacancy> findByUrl(String url);

    /**
     * Legacy rows (created before Sprint 8 Step 4B1) have {@code canonical_url = null} and are
     * never matched here - only {@link #findByUrl} finds those. See {@code VacancyCreationService}
     * for the combined lookup production code should use.
     */
    @Query("select v from Vacancy v where v.canonicalUrl = :#{#canonicalUrl.value()}")
    Optional<Vacancy> findByCanonicalUrl(@Param("canonicalUrl") CanonicalVacancyUrl canonicalUrl);

    @Query("select v from Vacancy v join fetch v.company where v.id = :id")
    Optional<Vacancy> findByIdWithCompany(UUID id);

    /**
     * Batch counterpart of {@link #findByIdWithCompany}, used to hydrate full {@link Vacancy}
     * instances (including their non-lazy {@code company}) for an already-ordered id list, e.g.
     * from {@code JobNotificationCandidateQueryAdapter}'s native id-selection query. Result order
     * is not guaranteed to match {@code ids}' order - callers that need that order must re-sort.
     */
    @Query("select v from Vacancy v join fetch v.company where v.id in :ids")
    List<Vacancy> findAllByIdWithCompany(@Param("ids") Collection<UUID> ids);

    /**
     * Atomically inserts a vacancy unless one with the same URL already exists, using the
     * partial unique index on {@code url} (uk_vacancy_url) as the sole source of truth for
     * novelty. Avoids the findByUrl()-then-save() check-then-act race across concurrent callers.
     *
     * <p>Also persists {@code vacancy.getCanonicalUrl()} verbatim - this method does not compute
     * or validate it, and does not check {@code canonical_url} for novelty itself; callers that
     * need canonical-URL-aware duplicate handling (a different, non-null {@code url} that still
     * shares a canonical identity with an existing row) must use {@code VacancyCreationService},
     * which performs that check before calling this method and translates the resulting
     * {@code uk_vacancy_canonical_url} unique-constraint violation - which this method does *not*
     * catch and lets propagate as a {@link org.springframework.dao.DataIntegrityViolationException}
     * - into the same duplicate outcome.
     */
    default VacancyPersistenceResult saveIfAbsent(Vacancy vacancy) {
        return insertVacancyIfAbsent(
                vacancy.getCompany().getId(),
                vacancy.getTitle(),
                vacancy.getDescription(),
                vacancy.getUrl(),
                vacancy.getCanonicalUrl(),
                vacancy.getLocation(),
                vacancy.getRemoteMode() == null ? null : vacancy.getRemoteMode().name(),
                vacancy.getSalaryMin(),
                vacancy.getSalaryMax(),
                vacancy.getCurrency(),
                vacancy.getSalaryText(),
                vacancy.getSource(),
                vacancy.getPostedAt())
                .map(VacancyPersistenceResult::inserted)
                .orElseGet(VacancyPersistenceResult::alreadyExists);
    }

    /**
     * Low-level primitive backing {@link #saveIfAbsent}. Relies on PostgreSQL's
     * {@code ON CONFLICT ... DO NOTHING} to resolve same-{@code url} duplicates entirely inside
     * the database engine, so a duplicate never raises a unique-constraint exception here for
     * {@code url} - id, created_at and updated_at are left to their column defaults, same as a
     * normal entity insert. The conflict target is deliberately still only {@code url}: a
     * same-{@code canonical_url}-but-different-{@code url} conflict is a genuinely different row
     * novelty question and is left to raise a real {@code uk_vacancy_canonical_url} violation,
     * for the caller to handle (see {@link #saveIfAbsent}).
     */
    @Query(value = """
            INSERT INTO vacancy (company_id, title, description, url, canonical_url, location, remote_mode, salary_min, salary_max, currency, salary_text, source, posted_at)
            VALUES (:companyId, :title, :description, :url, :canonicalUrl, :location, :remoteMode, :salaryMin, :salaryMax, :currency, :salaryText, :source, :postedAt)
            ON CONFLICT (url) WHERE url IS NOT NULL
            DO NOTHING
            RETURNING *
            """, nativeQuery = true)
    Optional<Vacancy> insertVacancyIfAbsent(
            @Param("companyId") UUID companyId,
            @Param("title") String title,
            @Param("description") String description,
            @Param("url") String url,
            @Param("canonicalUrl") String canonicalUrl,
            @Param("location") String location,
            @Param("remoteMode") String remoteMode,
            @Param("salaryMin") BigDecimal salaryMin,
            @Param("salaryMax") BigDecimal salaryMax,
            @Param("currency") String currency,
            @Param("salaryText") String salaryText,
            @Param("source") String source,
            @Param("postedAt") LocalDate postedAt);
}

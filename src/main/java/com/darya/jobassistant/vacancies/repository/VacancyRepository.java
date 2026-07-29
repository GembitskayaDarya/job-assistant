package com.darya.jobassistant.vacancies.repository;

import com.darya.jobassistant.vacancies.dto.VacancyPersistenceResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.maintenance.canonicalurl.LegacyVacancyUrlRow;
import com.darya.jobassistant.vacancies.maintenance.canonicalurl.PopulatedCanonicalUrlRow;
import com.darya.jobassistant.vacancies.url.CanonicalVacancyUrl;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VacancyRepository extends JpaRepository<Vacancy, UUID> {

    List<Vacancy> findByCompanyId(UUID companyId);

    List<Vacancy> findByTitleContainingIgnoreCase(String title);

    /**
     * Join-fetches {@code company} - {@code VacancyCreationService} may return whatever this
     * finds directly to a caller after its own isolated creation transaction has already closed,
     * so a plain lazy {@code company} reference would risk a {@code LazyInitializationException}
     * once accessed outside that transaction.
     */
    @Query("select v from Vacancy v join fetch v.company where v.url = :url")
    Optional<Vacancy> findByUrl(@Param("url") String url);

    /**
     * Legacy rows (created before Sprint 8 Step 4B1) have {@code canonical_url = null} and are
     * never matched here - only {@link #findByUrl} finds those. See {@code VacancyCreationService}
     * for the combined lookup production code should use. Join-fetches {@code company} for the
     * same reason as {@link #findByUrl}.
     *
     * <p>Delegates to a plain {@code String}-parameterized query rather than referencing {@code
     * canonicalUrl.value()} via a SpEL expression directly in {@code @Query} - that form was
     * tried first and, against a real PostgreSQL database, did not reliably filter by the given
     * value (observed returning every row with a non-null {@code canonical_url} instead of just
     * the matching one). A plain bound parameter has none of that risk.
     */
    default Optional<Vacancy> findByCanonicalUrl(CanonicalVacancyUrl canonicalUrl) {
        return findByCanonicalUrlValue(canonicalUrl.value());
    }

    @Query("select v from Vacancy v join fetch v.company where v.canonicalUrl = :canonicalUrlValue")
    Optional<Vacancy> findByCanonicalUrlValue(@Param("canonicalUrlValue") String canonicalUrlValue);

    /**
     * Lightweight existence-only pre-check by canonical URL - used by {@code JobDiscoveryService}
     * as a cost optimization before any paid Scrape/AI Extraction call, so a page already known to
     * be persisted is never re-fetched or re-extracted. Loads no columns beyond the existence
     * check itself - never {@code company}, {@code description}, or any other data, unlike {@link
     * #findByCanonicalUrl}. Not a substitute for {@link
     * com.darya.jobassistant.vacancies.service.VacancyCreationService}'s own transactional
     * lookup-then-insert: a concurrent transaction may still insert the same canonical URL between
     * this check and the caller's next action, which is why persistence still goes through the
     * canonical-conflict retry path regardless of what this method returned.
     */
    default boolean existsByCanonicalUrl(CanonicalVacancyUrl canonicalUrl) {
        return existsByCanonicalUrlValue(canonicalUrl.value());
    }

    @Query("select case when count(v) > 0 then true else false end from Vacancy v where v.canonicalUrl = :canonicalUrlValue")
    boolean existsByCanonicalUrlValue(@Param("canonicalUrlValue") String canonicalUrlValue);

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

    /**
     * Backs {@code VacancyCanonicalUrlAuditService}'s legacy-row scan: selects only {@code id} and
     * {@code url} - never {@code company}, description, salary, analysis, or application/
     * notification data - for rows with no canonical identity yet, ordered by {@code id} so paging
     * through {@code pageable} is deterministic regardless of concurrent writes elsewhere (the
     * audit itself runs in one {@code REPEATABLE_READ} transaction, so within a single run this
     * ordering is also stable against the audit's own snapshot).
     */
    @Query("""
            select new com.darya.jobassistant.vacancies.maintenance.canonicalurl.LegacyVacancyUrlRow(v.id, v.url)
            from Vacancy v
            where v.canonicalUrl is null
            order by v.id asc
            """)
    List<LegacyVacancyUrlRow> findLegacyCanonicalUrlRows(Pageable pageable);

    /**
     * Backs {@code VacancyCanonicalUrlAuditService}'s "currently used canonical identities" lookup:
     * every already-populated {@code canonical_url} alongside the {@code Vacancy} it belongs to,
     * without loading any other column. Not paginated - the audit needs the complete set resident
     * in memory for the whole run regardless (see that class's javadoc for the memory trade-off),
     * so paging this particular query would only add round-trips, not reduce peak memory.
     */
    @Query("""
            select new com.darya.jobassistant.vacancies.maintenance.canonicalurl.PopulatedCanonicalUrlRow(v.canonicalUrl, v.id)
            from Vacancy v
            where v.canonicalUrl is not null
            """)
    List<PopulatedCanonicalUrlRow> findPopulatedCanonicalUrlRows();

    /**
     * Backs {@code VacancyCanonicalUrlBackfillService}'s APPLY step: sets {@code canonical_url}
     * for exactly one row, only if it is still {@code NULL} - mirrors the conditional-update
     * pattern {@code VacancyImportSessionRepositoryCustom} uses for state transitions (e.g. {@code
     * completeIfWaitingForConfirmation}), so a row already backfilled by a concurrent run (or one
     * that no longer matches the plan) is never silently overwritten. {@code url} is never touched
     * - only {@code canonical_url} is set. Returns 0 if the row's {@code canonical_url} was already
     * non-null (or the row no longer exists); the caller treats anything other than exactly 1 as an
     * invariant violation.
     */
    @Modifying
    @Query("""
            update Vacancy v
            set v.canonicalUrl = :canonicalUrl
            where v.id = :vacancyId and v.canonicalUrl is null
            """)
    int setCanonicalUrlIfNull(@Param("vacancyId") UUID vacancyId, @Param("canonicalUrl") String canonicalUrl);
}

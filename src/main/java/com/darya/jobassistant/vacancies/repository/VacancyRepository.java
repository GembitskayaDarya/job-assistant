package com.darya.jobassistant.vacancies.repository;

import com.darya.jobassistant.vacancies.dto.VacancyPersistenceResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Query("select v from Vacancy v join fetch v.company where v.id = :id")
    Optional<Vacancy> findByIdWithCompany(UUID id);

    /**
     * Atomically inserts a vacancy unless one with the same URL already exists, using the
     * partial unique index on {@code url} (uk_vacancy_url) as the sole source of truth for
     * novelty. Avoids the findByUrl()-then-save() check-then-act race across concurrent callers.
     */
    default VacancyPersistenceResult saveIfAbsent(Vacancy vacancy) {
        return insertVacancyIfAbsent(
                vacancy.getCompany().getId(),
                vacancy.getTitle(),
                vacancy.getDescription(),
                vacancy.getUrl(),
                vacancy.getSalaryMin(),
                vacancy.getSalaryMax(),
                vacancy.getCurrency(),
                vacancy.getSource(),
                vacancy.getPostedAt())
                .map(VacancyPersistenceResult::inserted)
                .orElseGet(VacancyPersistenceResult::alreadyExists);
    }

    /**
     * Low-level primitive backing {@link #saveIfAbsent}. Relies on PostgreSQL's
     * {@code ON CONFLICT ... DO NOTHING} to resolve duplicates entirely inside the database
     * engine, so a duplicate never raises a unique-constraint exception here - id, created_at
     * and updated_at are left to their column defaults, same as a normal entity insert.
     */
    @Query(value = """
            INSERT INTO vacancy (company_id, title, description, url, salary_min, salary_max, currency, source, posted_at)
            VALUES (:companyId, :title, :description, :url, :salaryMin, :salaryMax, :currency, :source, :postedAt)
            ON CONFLICT (url) WHERE url IS NOT NULL
            DO NOTHING
            RETURNING *
            """, nativeQuery = true)
    Optional<Vacancy> insertVacancyIfAbsent(
            @Param("companyId") UUID companyId,
            @Param("title") String title,
            @Param("description") String description,
            @Param("url") String url,
            @Param("salaryMin") BigDecimal salaryMin,
            @Param("salaryMax") BigDecimal salaryMax,
            @Param("currency") String currency,
            @Param("source") String source,
            @Param("postedAt") LocalDate postedAt);
}

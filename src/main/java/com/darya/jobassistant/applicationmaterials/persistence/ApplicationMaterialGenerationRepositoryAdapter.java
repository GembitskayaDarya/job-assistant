package com.darya.jobassistant.applicationmaterials.persistence;

import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGeneration;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationConcurrentModificationException;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationRepositoryPort;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationVacancyNotFoundException;
import com.darya.jobassistant.applicationmaterials.entity.ApplicationMaterialGenerationEntity;
import com.darya.jobassistant.applicationmaterials.repository.ApplicationMaterialGenerationRepository;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL/JPA adapter for {@link ApplicationMaterialGenerationRepositoryPort} - Sprint 10
 * Step 1. Not wired into any Telegram or AI-generation workflow yet: this step is persistence
 * foundation only.
 *
 * <p>Depends on {@link VacancyRepository} purely for the create-time "does this vacancy exist"
 * check, matching {@code CareerHistoryRepositoryAdapter}'s "one adapter per aggregate, built from
 * its own table-level repository plus the parent it references" convention.
 */
@Repository
@RequiredArgsConstructor
public class ApplicationMaterialGenerationRepositoryAdapter implements ApplicationMaterialGenerationRepositoryPort {

    private final ApplicationMaterialGenerationRepository generationRepository;
    private final VacancyRepository vacancyRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Optional<ApplicationMaterialGeneration> findById(UUID id) {
        return generationRepository.findById(id).map(ApplicationMaterialGenerationPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationMaterialGeneration> findByVacancyId(UUID vacancyId) {
        return generationRepository.findAllByVacancyIdOrderByRequestedAtDesc(vacancyId).stream()
                .map(ApplicationMaterialGenerationPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public ApplicationMaterialGeneration save(ApplicationMaterialGeneration generation) {
        ApplicationMaterialGenerationEntity savedEntity =
                generation.id() == null ? createRow(generation) : updateRow(generation);
        return ApplicationMaterialGenerationPersistenceMapper.toDomain(savedEntity);
    }

    /**
     * A {@code null} {@link ApplicationMaterialGeneration#id()} means "not yet persisted" -
     * inserts a new row after confirming {@code vacancyId} references a real Vacancy, matching
     * {@code CareerHistoryRepositoryAdapter#createRoot}'s pre-check-then-insert convention.
     */
    private ApplicationMaterialGenerationEntity createRow(ApplicationMaterialGeneration generation) {
        Vacancy vacancy = vacancyRepository.findById(generation.vacancyId())
                .orElseThrow(() -> new ApplicationMaterialGenerationVacancyNotFoundException(generation.vacancyId()));
        return generationRepository.save(ApplicationMaterialGenerationPersistenceMapper.toNewEntity(generation, vacancy));
    }

    /**
     * A non-null id means "update" - performs an explicit, unconditional version-checked write of
     * every field via {@link ApplicationMaterialGenerationRepository#updateIfVersionMatches}, using
     * the caller-supplied {@link ApplicationMaterialGeneration#version()} as the expected version.
     * Zero rows updated means the row either no longer exists or {@code expectedVersion} is stale -
     * either way, translated to {@link ApplicationMaterialGenerationConcurrentModificationException}.
     */
    private ApplicationMaterialGenerationEntity updateRow(ApplicationMaterialGeneration generation) {
        int updatedRows = generationRepository.updateIfVersionMatches(
                generation.id(),
                generation.status(),
                generation.candidateProfileVersion(),
                generation.careerHistoryVersion(),
                generation.startedAt(),
                generation.completedAt(),
                generation.failureCode(),
                generation.failureMessage(),
                Instant.now(clock),
                generation.version());
        if (updatedRows == 0) {
            throw concurrentModification(generation);
        }
        // Re-fetched rather than reused from before the update: the modifying query above is a
        // bulk JPQL statement, so it never populates or refreshes any managed entity instance
        // itself - this is the first (and only) point where a real, current
        // ApplicationMaterialGenerationEntity for this id becomes available in this transaction.
        return generationRepository.findById(generation.id())
                .orElseThrow(() -> concurrentModification(generation));
    }

    private ApplicationMaterialGenerationConcurrentModificationException concurrentModification(ApplicationMaterialGeneration generation) {
        return new ApplicationMaterialGenerationConcurrentModificationException(
                generation.id(), generation.vacancyId(), generation.version());
    }
}

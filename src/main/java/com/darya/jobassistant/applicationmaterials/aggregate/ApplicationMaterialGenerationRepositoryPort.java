package com.darya.jobassistant.applicationmaterials.aggregate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application/domain-facing persistence port for {@link ApplicationMaterialGeneration} - backed by
 * V21's {@code application_material_generation} table. Returns and accepts {@link
 * ApplicationMaterialGeneration} only - never a JPA entity, never a Spring Data type.
 *
 * <p>Not yet wired into any Telegram or AI-generation workflow - this Sprint 10 step is
 * persistence foundation only.
 */
public interface ApplicationMaterialGenerationRepositoryPort {

    /**
     * Loads one generation by id, or {@link Optional#empty()} if no row has this id.
     */
    Optional<ApplicationMaterialGeneration> findById(UUID id);

    /**
     * Loads every generation for {@code vacancyId}, newest requested first ({@code
     * requestedAt DESC}) - a Vacancy may have any number of generations over time (see {@link
     * ApplicationMaterialGeneration}'s javadoc).
     */
    List<ApplicationMaterialGeneration> findByVacancyId(UUID vacancyId);

    /**
     * Persists {@code generation} as the complete desired state: creates a new row when {@link
     * ApplicationMaterialGeneration#id()} is {@code null}, otherwise performs a version-checked
     * update of the whole row and increments {@link ApplicationMaterialGeneration#version()}.
     *
     * @return the persisted generation, with its durable id and current (post-save) version
     * @throws ApplicationMaterialGenerationConcurrentModificationException if {@code generation}
     *     carries a version that no longer matches the row's current version - another
     *     transaction modified it first
     * @throws ApplicationMaterialGenerationVacancyNotFoundException if creating a new generation
     *     for a {@code vacancyId} with no matching Vacancy
     */
    ApplicationMaterialGeneration save(ApplicationMaterialGeneration generation);
}

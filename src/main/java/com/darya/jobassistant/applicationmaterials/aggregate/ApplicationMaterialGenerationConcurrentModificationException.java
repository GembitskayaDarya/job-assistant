package com.darya.jobassistant.applicationmaterials.aggregate;

import java.util.UUID;

/**
 * Thrown by {@link ApplicationMaterialGenerationRepositoryPort#save} when the supplied {@link
 * ApplicationMaterialGeneration#version()} no longer matches the row's current version - another
 * transaction committed a change in between. Framework-free at this boundary, matching {@code
 * CareerHistoryConcurrentModificationException}'s convention: the persistence adapter never lets a
 * JPA/Hibernate exception type escape through the port. Carries only safe identifiers - generation
 * id, vacancy id, and the expected version - never any generation content.
 */
public class ApplicationMaterialGenerationConcurrentModificationException extends RuntimeException {

    public ApplicationMaterialGenerationConcurrentModificationException(UUID generationId, UUID vacancyId, long expectedVersion) {
        super("Application material generation '" + generationId + "' (vacancyId=" + vacancyId
                + ") was concurrently modified by another transaction - expected version " + expectedVersion
                + " no longer matches");
    }
}

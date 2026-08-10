package com.darya.jobassistant.applicationmaterials.aggregate;

import java.util.UUID;

/**
 * Thrown by {@link ApplicationMaterialGenerationRepositoryPort#save} when creating a new
 * generation that references a {@code vacancyId} with no matching Vacancy row - {@code
 * application_material_generation.vacancy_id}'s foreign key (V21) is the actual enforcement; this
 * translates that database relationship into a framework-free signal rather than letting a raw
 * {@code DataIntegrityViolationException} escape the port.
 */
public class ApplicationMaterialGenerationVacancyNotFoundException extends RuntimeException {

    public ApplicationMaterialGenerationVacancyNotFoundException(UUID vacancyId) {
        super("No vacancy exists for vacancyId '" + vacancyId + "' - cannot create an application material generation for it");
    }
}

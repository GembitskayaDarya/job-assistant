package com.darya.jobassistant.applicationmaterials.persistence;

import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGeneration;
import com.darya.jobassistant.applicationmaterials.entity.ApplicationMaterialGenerationEntity;
import com.darya.jobassistant.vacancies.entity.Vacancy;

/**
 * Stateless entity &lt;-&gt; domain mapping for {@link ApplicationMaterialGeneration}. No
 * repository calls, no transaction management - {@link ApplicationMaterialGenerationRepositoryAdapter}
 * owns both, matching {@code CareerHistoryPersistenceMapper}'s convention.
 */
final class ApplicationMaterialGenerationPersistenceMapper {

    private ApplicationMaterialGenerationPersistenceMapper() {
    }

    static ApplicationMaterialGeneration toDomain(ApplicationMaterialGenerationEntity entity) {
        return new ApplicationMaterialGeneration(
                entity.getId(),
                entity.getVacancy().getId(),
                entity.getStatus(),
                entity.getCandidateProfileVersion(),
                entity.getCareerHistoryVersion(),
                entity.getSourceFingerprint(),
                entity.getRequestedAt(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getFailureCode(),
                entity.getFailureMessage(),
                entity.getVersion());
    }

    /**
     * A brand-new, not-yet-persisted row - id left null (unless the caller already preserved one)
     * so Hibernate generates it. Only called when {@link ApplicationMaterialGeneration#id()} is
     * {@code null} - see {@code ApplicationMaterialGenerationRepositoryAdapter#createRow}.
     */
    static ApplicationMaterialGenerationEntity toNewEntity(ApplicationMaterialGeneration generation, Vacancy vacancy) {
        return ApplicationMaterialGenerationEntity.builder()
                .id(generation.id())
                .vacancy(vacancy)
                .status(generation.status())
                .candidateProfileVersion(generation.candidateProfileVersion())
                .careerHistoryVersion(generation.careerHistoryVersion())
                .sourceFingerprint(generation.sourceFingerprint())
                .requestedAt(generation.requestedAt())
                .startedAt(generation.startedAt())
                .completedAt(generation.completedAt())
                .failureCode(generation.failureCode())
                .failureMessage(generation.failureMessage())
                .build();
    }
}

package com.darya.jobassistant.applicationmaterials.result.persistence;

import com.darya.jobassistant.applicationmaterials.entity.ApplicationMaterialGenerationEntity;
import com.darya.jobassistant.applicationmaterials.result.aggregate.ApplicationMaterialGenerationResult;
import com.darya.jobassistant.applicationmaterials.result.entity.ApplicationMaterialGenerationResultEntity;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Entity &lt;-&gt; domain mapping for {@link ApplicationMaterialGenerationResult} - no repository
 * calls, no transaction management; {@link ApplicationMaterialGenerationResultRepositoryAdapter}
 * owns both, matching {@code CareerHistoryPersistenceMapper}'s convention. Unlike that fully static
 * mapper, this one is {@code @Component}-based because it delegates JSONB (de)serialization to
 * {@link ApplicationMaterialGenerationResultContentMapper}, which itself needs the shared {@code
 * ObjectMapper} bean injected.
 */
@Component
@RequiredArgsConstructor
class ApplicationMaterialGenerationResultPersistenceMapper {

    private final ApplicationMaterialGenerationResultContentMapper contentMapper;

    ApplicationMaterialGenerationResult toDomain(ApplicationMaterialGenerationResultEntity entity) {
        return new ApplicationMaterialGenerationResult(
                entity.getId(),
                entity.getGeneration().getId(),
                entity.getSchemaVersion(),
                contentMapper.readCv(entity.getCvContent()),
                contentMapper.readCoverLetter(entity.getCoverLetterContent()),
                entity.getAiProvider(),
                entity.getAiModel(),
                entity.getPromptVersion(),
                entity.getGeneratedAt(),
                entity.getCreatedAt());
    }

    /** Only called for a brand-new result ({@link ApplicationMaterialGenerationResult#id()} is {@code null}) - results are write-once. */
    ApplicationMaterialGenerationResultEntity toNewEntity(
            ApplicationMaterialGenerationResult result, ApplicationMaterialGenerationEntity generation, Instant createdAt) {
        return ApplicationMaterialGenerationResultEntity.builder()
                .generation(generation)
                .schemaVersion(result.schemaVersion())
                .cvContent(contentMapper.writeCv(result.cv()))
                .coverLetterContent(contentMapper.writeCoverLetter(result.coverLetter()))
                .aiProvider(result.aiProvider())
                .aiModel(result.aiModel())
                .promptVersion(result.promptVersion())
                .generatedAt(result.generatedAt())
                .createdAt(createdAt)
                .build();
    }
}

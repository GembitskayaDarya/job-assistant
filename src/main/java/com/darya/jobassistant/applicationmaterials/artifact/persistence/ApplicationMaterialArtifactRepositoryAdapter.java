package com.darya.jobassistant.applicationmaterials.artifact.persistence;

import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationNotFoundException;
import com.darya.jobassistant.applicationmaterials.artifact.aggregate.ApplicationMaterialArtifact;
import com.darya.jobassistant.applicationmaterials.artifact.aggregate.ApplicationMaterialArtifactAlreadyExistsException;
import com.darya.jobassistant.applicationmaterials.artifact.aggregate.ApplicationMaterialArtifactRepositoryPort;
import com.darya.jobassistant.applicationmaterials.artifact.entity.ApplicationMaterialArtifactEntity;
import com.darya.jobassistant.applicationmaterials.artifact.repository.ApplicationMaterialArtifactRepository;
import com.darya.jobassistant.applicationmaterials.entity.ApplicationMaterialGenerationEntity;
import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialFormat;
import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialType;
import com.darya.jobassistant.applicationmaterials.repository.ApplicationMaterialGenerationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL/JPA adapter for {@link ApplicationMaterialArtifactRepositoryPort} - Sprint 10 Step 4.
 * Write-once per natural key: {@link #save} always inserts, translating a lost concurrent-rendering
 * race (two requests for the exact same generation/material/format/rendererVersion) into {@link
 * ApplicationMaterialArtifactAlreadyExistsException} for the caller to resolve by reloading -
 * mirrors {@code ApplicationMaterialRenderSnapshotRepositoryAdapter}'s convention exactly.
 */
@Repository
@RequiredArgsConstructor
public class ApplicationMaterialArtifactRepositoryAdapter implements ApplicationMaterialArtifactRepositoryPort {

    /** Must match the constraint name in {@code V24__create_application_material_artifact.sql}. */
    private static final String NATURAL_KEY_UNIQUE_INDEX_NAME = "uk_application_material_artifact_natural_key";

    private final ApplicationMaterialArtifactRepository artifactRepository;
    private final ApplicationMaterialGenerationRepository generationRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Optional<ApplicationMaterialArtifact> find(
            UUID generationId, ApplicationMaterialType materialType, ApplicationMaterialFormat format, int rendererVersion) {
        return artifactRepository
                .findByGenerationIdAndMaterialTypeAndFormatAndRendererVersion(generationId, materialType, format, rendererVersion)
                .map(ApplicationMaterialArtifactPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApplicationMaterialArtifact> findByGenerationId(UUID generationId) {
        return artifactRepository.findByGenerationId(generationId).stream()
                .map(ApplicationMaterialArtifactPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public ApplicationMaterialArtifact save(ApplicationMaterialArtifact artifact) {
        if (artifact.id() != null) {
            throw new IllegalArgumentException("Application material artifacts are write-once and cannot be updated");
        }
        ApplicationMaterialGenerationEntity generation = generationRepository.findById(artifact.generationId())
                .orElseThrow(() -> new ApplicationMaterialGenerationNotFoundException(artifact.generationId()));
        try {
            ApplicationMaterialArtifactEntity saved = artifactRepository.saveAndFlush(
                    ApplicationMaterialArtifactPersistenceMapper.toNewEntity(artifact, generation, Instant.now(clock)));
            return ApplicationMaterialArtifactPersistenceMapper.toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            if (isNaturalKeyConflict(e)) {
                throw new ApplicationMaterialArtifactAlreadyExistsException(
                        artifact.generationId(), artifact.materialType(), artifact.format(), artifact.rendererVersion());
            }
            throw e;
        }
    }

    private boolean isNaturalKeyConflict(DataIntegrityViolationException e) {
        ConstraintViolationException constraintViolation = findConstraintViolationException(e);
        if (constraintViolation != null && constraintViolation.getConstraintName() != null) {
            return NATURAL_KEY_UNIQUE_INDEX_NAME.equals(constraintViolation.getConstraintName());
        }
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(NATURAL_KEY_UNIQUE_INDEX_NAME);
    }

    private ConstraintViolationException findConstraintViolationException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolationException) {
                return constraintViolationException;
            }
            current = current.getCause();
        }
        return null;
    }
}

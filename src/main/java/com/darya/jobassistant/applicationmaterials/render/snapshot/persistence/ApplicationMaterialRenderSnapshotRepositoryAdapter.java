package com.darya.jobassistant.applicationmaterials.render.snapshot.persistence;

import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationNotFoundException;
import com.darya.jobassistant.applicationmaterials.entity.ApplicationMaterialGenerationEntity;
import com.darya.jobassistant.applicationmaterials.render.snapshot.aggregate.ApplicationMaterialRenderSnapshot;
import com.darya.jobassistant.applicationmaterials.render.snapshot.aggregate.ApplicationMaterialRenderSnapshotAlreadyExistsException;
import com.darya.jobassistant.applicationmaterials.render.snapshot.aggregate.ApplicationMaterialRenderSnapshotRepositoryPort;
import com.darya.jobassistant.applicationmaterials.render.snapshot.entity.ApplicationMaterialRenderSnapshotEntity;
import com.darya.jobassistant.applicationmaterials.render.snapshot.repository.ApplicationMaterialRenderSnapshotRepository;
import com.darya.jobassistant.applicationmaterials.repository.ApplicationMaterialGenerationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL/JPA adapter for {@link ApplicationMaterialRenderSnapshotRepositoryPort} - Sprint 10
 * Step 4. Write-once: {@link #save} always inserts (never updates), rejecting an already-persisted
 * snapshot outright and translating a lost concurrent-creation race into {@link
 * ApplicationMaterialRenderSnapshotAlreadyExistsException} for the caller to resolve by reloading -
 * mirrors {@code ApplicationMaterialGenerationResultRepositoryAdapter} exactly.
 */
@Repository
@RequiredArgsConstructor
public class ApplicationMaterialRenderSnapshotRepositoryAdapter implements ApplicationMaterialRenderSnapshotRepositoryPort {

    /** Must match the constraint name in {@code V23__create_application_material_render_snapshot.sql}. */
    private static final String GENERATION_ID_UNIQUE_INDEX_NAME = "uk_application_material_render_snapshot_generation_id";

    private final ApplicationMaterialRenderSnapshotRepository snapshotRepository;
    private final ApplicationMaterialGenerationRepository generationRepository;
    private final ApplicationMaterialRenderSnapshotPersistenceMapper mapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Optional<ApplicationMaterialRenderSnapshot> findByGenerationId(UUID generationId) {
        return snapshotRepository.findByGenerationId(generationId).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public ApplicationMaterialRenderSnapshot save(ApplicationMaterialRenderSnapshot snapshot) {
        if (snapshot.id() != null) {
            throw new IllegalArgumentException("Application material render snapshots are write-once and cannot be updated");
        }
        ApplicationMaterialGenerationEntity generation = generationRepository.findById(snapshot.generationId())
                .orElseThrow(() -> new ApplicationMaterialGenerationNotFoundException(snapshot.generationId()));
        try {
            ApplicationMaterialRenderSnapshotEntity saved = snapshotRepository.saveAndFlush(
                    mapper.toNewEntity(snapshot, generation, Instant.now(clock)));
            return mapper.toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            if (isGenerationConflict(e)) {
                throw new ApplicationMaterialRenderSnapshotAlreadyExistsException(snapshot.generationId());
            }
            throw e;
        }
    }

    private boolean isGenerationConflict(DataIntegrityViolationException e) {
        ConstraintViolationException constraintViolation = findConstraintViolationException(e);
        if (constraintViolation != null && constraintViolation.getConstraintName() != null) {
            return GENERATION_ID_UNIQUE_INDEX_NAME.equals(constraintViolation.getConstraintName());
        }
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(GENERATION_ID_UNIQUE_INDEX_NAME);
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

package com.darya.jobassistant.applicationmaterials.result.persistence;

import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationNotFoundException;
import com.darya.jobassistant.applicationmaterials.entity.ApplicationMaterialGenerationEntity;
import com.darya.jobassistant.applicationmaterials.repository.ApplicationMaterialGenerationRepository;
import com.darya.jobassistant.applicationmaterials.result.aggregate.ApplicationMaterialGenerationResult;
import com.darya.jobassistant.applicationmaterials.result.aggregate.ApplicationMaterialGenerationResultAlreadyExistsException;
import com.darya.jobassistant.applicationmaterials.result.aggregate.ApplicationMaterialGenerationResultRepositoryPort;
import com.darya.jobassistant.applicationmaterials.result.entity.ApplicationMaterialGenerationResultEntity;
import com.darya.jobassistant.applicationmaterials.result.repository.ApplicationMaterialGenerationResultRepository;
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
 * PostgreSQL/JPA adapter for {@link ApplicationMaterialGenerationResultRepositoryPort} - Sprint 10
 * Step 3. Write-once: {@link #save} always inserts (never updates - see {@link
 * ApplicationMaterialGenerationResult}'s javadoc), rejecting an already-persisted result outright.
 */
@Repository
@RequiredArgsConstructor
public class ApplicationMaterialGenerationResultRepositoryAdapter implements ApplicationMaterialGenerationResultRepositoryPort {

    /** Must match the constraint name in {@code V22__create_application_material_generation_result.sql}. */
    private static final String GENERATION_ID_UNIQUE_INDEX_NAME = "uk_application_material_generation_result_generation_id";

    private final ApplicationMaterialGenerationResultRepository resultRepository;
    private final ApplicationMaterialGenerationRepository generationRepository;
    private final ApplicationMaterialGenerationResultPersistenceMapper mapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Optional<ApplicationMaterialGenerationResult> findByGenerationId(UUID generationId) {
        return resultRepository.findByGenerationId(generationId).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public ApplicationMaterialGenerationResult save(ApplicationMaterialGenerationResult result) {
        if (result.id() != null) {
            throw new IllegalArgumentException("Application material generation results are write-once and cannot be updated");
        }
        ApplicationMaterialGenerationEntity generation = generationRepository.findById(result.generationId())
                .orElseThrow(() -> new ApplicationMaterialGenerationNotFoundException(result.generationId()));
        try {
            ApplicationMaterialGenerationResultEntity saved = resultRepository.saveAndFlush(
                    mapper.toNewEntity(result, generation, Instant.now(clock)));
            return mapper.toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            if (isGenerationConflict(e)) {
                throw new ApplicationMaterialGenerationResultAlreadyExistsException(result.generationId());
            }
            throw e;
        }
    }

    /**
     * Same two-tier constraint-name detection strategy as {@code
     * CareerHistoryRepositoryAdapter#isCandidateProfileConflict}: prefers Hibernate's own
     * structural extraction, falling back to matching the constraint name in the deepest {@link
     * java.sql.SQLException}'s message.
     */
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

package com.darya.jobassistant.ai.repository;

import com.darya.jobassistant.ai.entity.JobAnalysisEntity;
import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.ai.model.PersistedJobAnalysis;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Application-facing JobAnalysis persistence contract. Callers should only rely on
 * {@link #persist}, which exclusively uses domain types ({@link JobAnalysis} and
 * {@link PersistedJobAnalysis}), never the JPA entity. The {@code JpaRepository} superinterface
 * is an implementation detail that lets this interface stay the single port + adapter, per this
 * project's repository convention (see {@code NotificationDeliveryRepository}).
 */
public interface JobAnalysisRepository extends JpaRepository<JobAnalysisEntity, UUID> {

    Optional<JobAnalysisEntity> findByVacancyId(UUID vacancyId);

    List<JobAnalysisEntity> findByVacancyIdIn(Collection<UUID> vacancyIds);

    /**
     * Persists the authoritative analysis for a vacancy. {@code vacancy_id} is unique, so this
     * relies on the plain JPA insert path (no upsert) - re-analysis of an already-analyzed
     * vacancy is a separate future concern, not handled here.
     */
    default PersistedJobAnalysis persist(UUID vacancyId, JobAnalysis analysis) {
        JobAnalysisEntity saved = save(JobAnalysisEntity.builder()
                .vacancyId(vacancyId)
                .score(analysis.score())
                .summary(analysis.summary())
                .pros(analysis.pros())
                .cons(analysis.cons())
                .missingSkills(analysis.missingSkills())
                .build());
        return toDomain(saved);
    }

    static PersistedJobAnalysis toDomain(JobAnalysisEntity entity) {
        return new PersistedJobAnalysis(
                entity.getId(),
                entity.getVacancyId(),
                new JobAnalysis(entity.getScore(), entity.getPros(), entity.getCons(), entity.getMissingSkills(), entity.getSummary()),
                entity.getCreatedAt());
    }
}

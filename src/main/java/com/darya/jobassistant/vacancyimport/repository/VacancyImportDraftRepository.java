package com.darya.jobassistant.vacancyimport.repository;

import com.darya.jobassistant.vacancyextraction.model.ExtractedVacancyData;
import com.darya.jobassistant.vacancyimport.entity.VacancyImportDraftEntity;
import com.darya.jobassistant.vacancyimport.model.VacancyImportDraft;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Application-facing VacancyImportDraft persistence contract. Callers should only rely on
 * {@link #saveDraft} and {@link #findDraftBySessionId}, which exclusively use domain types
 * ({@link VacancyImportDraft}, {@link ExtractedVacancyData}), never the JPA entity. Follows the
 * same single port + adapter convention as {@code JobAnalysisRepository}.
 */
public interface VacancyImportDraftRepository extends JpaRepository<VacancyImportDraftEntity, UUID> {

    Optional<VacancyImportDraftEntity> findBySessionId(UUID sessionId);

    /**
     * Inserts the draft for a session. {@code session_id} is unique, so a second concurrent call
     * for the same session fails with a constraint violation rather than silently overwriting the
     * first - callers (see {@code VacancyImportService.extract}) are expected to catch that and
     * load the winning draft instead of retrying the insert.
     */
    default VacancyImportDraft saveDraft(UUID sessionId, ExtractedVacancyData data, Instant now) {
        VacancyImportDraftEntity saved = save(VacancyImportDraftEntity.builder()
                .sessionId(sessionId)
                .title(data.title())
                .company(data.company())
                .location(data.location())
                .remotePolicy(data.remotePolicy())
                .contractTypes(data.contractTypes())
                .requiredSkills(data.requiredSkills())
                .salaryText(data.salaryText())
                .createdAt(now)
                .updatedAt(now)
                .build());
        return toDomain(saved);
    }

    default Optional<VacancyImportDraft> findDraftBySessionId(UUID sessionId) {
        return findBySessionId(sessionId).map(VacancyImportDraftRepository::toDomain);
    }

    static VacancyImportDraft toDomain(VacancyImportDraftEntity entity) {
        return new VacancyImportDraft(
                entity.getId(),
                entity.getSessionId(),
                new ExtractedVacancyData(
                        entity.getTitle(),
                        entity.getCompany(),
                        entity.getLocation(),
                        entity.getRemotePolicy(),
                        entity.getContractTypes(),
                        entity.getRequiredSkills(),
                        entity.getSalaryText()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}

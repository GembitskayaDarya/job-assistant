package com.darya.jobassistant.notifications.query;

import com.darya.jobassistant.ai.entity.JobAnalysisEntity;
import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.ai.model.PersistedJobAnalysis;
import com.darya.jobassistant.ai.repository.JobAnalysisRepository;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL-backed {@link JobNotificationCandidateQueryPort}. The eligibility/ordering/limit
 * logic (score threshold, the "no existing delivery" anti-join, deterministic ordering, bounding)
 * is genuinely PostgreSQL-specific, so it runs as a single native query that returns only vacancy
 * ids in the required order - never a native-query result type crosses the port boundary. Those
 * ids are then hydrated into full, already-tested {@link Vacancy} and {@link JobAnalysisEntity}
 * instances via the existing {@link VacancyRepository} and {@link JobAnalysisRepository}, which
 * avoids hand-mapping a multi-table {@code Object[]} row by column position.
 */
@Repository
@RequiredArgsConstructor
public class JobNotificationCandidateQueryAdapter implements JobNotificationCandidateQueryPort {

    /**
     * {@code job_analysis.vacancy_id} is UNIQUE, so this join can never multiply a vacancy row -
     * each matching vacancy contributes exactly one (vacancy, analysis) pair, which is why no
     * "pick the latest analysis" logic is needed here.
     *
     * <p>Ordering: score DESC (best matches first), then {@code job_analysis.created_at} ASC as
     * the stable age tiebreaker for equal scores (the analysis that has been sitting in the
     * backlog longest goes first), then {@code vacancy.id} ASC as the final deterministic
     * tiebreaker so identical inputs always produce the same order.
     */
    private static final String FIND_CANDIDATE_VACANCY_IDS_SQL = """
            SELECT v.id
            FROM vacancy v
            JOIN job_analysis ja ON ja.vacancy_id = v.id
            WHERE ja.score >= :minimumScore
              AND NOT EXISTS (
                  SELECT 1
                  FROM notification_delivery nd
                  WHERE nd.vacancy_id = v.id
                    AND nd.recipient_chat_id = :recipientChatId
              )
            ORDER BY ja.score DESC, ja.created_at ASC, v.id ASC
            LIMIT :limit
            """;

    private final VacancyRepository vacancyRepository;
    private final JobAnalysisRepository jobAnalysisRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<JobNotificationCandidate> findCandidates(Long recipientChatId, int minimumScore, int limit) {
        validateQueryParameters(recipientChatId, minimumScore, limit);

        List<UUID> orderedVacancyIds = findMatchingVacancyIdsInOrder(recipientChatId, minimumScore, limit);
        if (orderedVacancyIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, Vacancy> vacanciesById = vacancyRepository.findAllByIdWithCompany(orderedVacancyIds).stream()
                .collect(Collectors.toMap(Vacancy::getId, Function.identity()));
        Map<UUID, JobAnalysisEntity> analysesByVacancyId = jobAnalysisRepository.findByVacancyIdIn(orderedVacancyIds).stream()
                .collect(Collectors.toMap(JobAnalysisEntity::getVacancyId, Function.identity()));

        return orderedVacancyIds.stream()
                .map(id -> toCandidate(vacanciesById.get(id), analysesByVacancyId.get(id)))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<UUID> findMatchingVacancyIdsInOrder(Long recipientChatId, int minimumScore, int limit) {
        return entityManager.createNativeQuery(FIND_CANDIDATE_VACANCY_IDS_SQL)
                .setParameter("recipientChatId", recipientChatId)
                .setParameter("minimumScore", minimumScore)
                .setParameter("limit", limit)
                .getResultList();
    }

    private JobNotificationCandidate toCandidate(Vacancy vacancy, JobAnalysisEntity analysisEntity) {
        PersistedJobAnalysis analysis = new PersistedJobAnalysis(
                analysisEntity.getId(),
                analysisEntity.getVacancyId(),
                new JobAnalysis(
                        analysisEntity.getScore(),
                        analysisEntity.getPros(),
                        analysisEntity.getCons(),
                        analysisEntity.getMissingSkills(),
                        analysisEntity.getSummary()),
                analysisEntity.getCreatedAt());
        return new JobNotificationCandidate(vacancy, analysis);
    }

    static void validateQueryParameters(Long recipientChatId, int minimumScore, int limit) {
        if (recipientChatId == null || recipientChatId == 0L) {
            throw new IllegalArgumentException("recipientChatId must be a valid Telegram chat ID");
        }
        if (minimumScore < 0 || minimumScore > 100) {
            throw new IllegalArgumentException(
                    "minimumScore must be between 0 and 100, but was " + minimumScore);
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }
    }
}

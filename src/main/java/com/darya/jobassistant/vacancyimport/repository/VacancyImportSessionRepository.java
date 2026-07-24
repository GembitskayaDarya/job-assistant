package com.darya.jobassistant.vacancyimport.repository;

import com.darya.jobassistant.vacancyimport.entity.VacancyImportSessionEntity;
import com.darya.jobassistant.vacancyimport.model.ImportState;
import com.darya.jobassistant.vacancyimport.model.VacancyImportSession;
import com.darya.jobassistant.vacancyimport.model.VacancyImportSessionMapper;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Application-facing VacancyImportSession persistence contract. Callers should only rely on
 * {@link #saveSession}, {@link #findSessionById}, {@link #findActiveSession} and
 * {@link #findExpiredActiveSessions} - these exclusively use the domain type
 * {@link VacancyImportSession}, never {@link VacancyImportSessionEntity}. The
 * {@code JpaRepository} superinterface is an implementation detail that lets this interface
 * stay the single port + adapter, per this project's repository convention (see
 * {@code JobAnalysisRepository}, {@code NotificationDeliveryRepository}).
 */
public interface VacancyImportSessionRepository extends JpaRepository<VacancyImportSessionEntity, UUID> {

    List<VacancyImportSessionEntity> findByTelegramChatIdAndTelegramUserIdAndStateIn(
            Long telegramChatId, Long telegramUserId, Collection<ImportState> states);

    List<VacancyImportSessionEntity> findByStateInAndExpiresAtBefore(Collection<ImportState> states, Instant threshold);

    default VacancyImportSession saveSession(VacancyImportSession session) {
        VacancyImportSessionEntity saved = save(VacancyImportSessionMapper.toEntity(session));
        return VacancyImportSessionMapper.toDomain(saved);
    }

    default Optional<VacancyImportSession> findSessionById(UUID id) {
        return findById(id).map(VacancyImportSessionMapper::toDomain);
    }

    /**
     * The active (non-terminal) session for a Telegram chat+user pair, if any. At most one can
     * exist - enforced by {@code uk_vacancy_import_session_active_chat_user} - so picking the
     * first result is safe rather than arbitrary.
     */
    default Optional<VacancyImportSession> findActiveSession(Long telegramChatId, Long telegramUserId) {
        return findByTelegramChatIdAndTelegramUserIdAndStateIn(telegramChatId, telegramUserId, ImportState.activeStates())
                .stream()
                .findFirst()
                .map(VacancyImportSessionMapper::toDomain);
    }

    default List<VacancyImportSession> findExpiredActiveSessions(Instant now) {
        return findByStateInAndExpiresAtBefore(ImportState.activeStates(), now).stream()
                .map(VacancyImportSessionMapper::toDomain)
                .toList();
    }
}

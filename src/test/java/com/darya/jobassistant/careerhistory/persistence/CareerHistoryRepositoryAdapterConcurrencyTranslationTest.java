package com.darya.jobassistant.careerhistory.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.candidates.repository.CandidateProfileRepository;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryConcurrentModificationException;
import com.darya.jobassistant.careerhistory.repository.CareerCompanyRepository;
import com.darya.jobassistant.careerhistory.repository.CareerHistoryRepository;
import com.darya.jobassistant.careerhistory.repository.CareerPositionAchievementRepository;
import com.darya.jobassistant.careerhistory.repository.CareerPositionRepository;
import com.darya.jobassistant.careerhistory.repository.CareerPositionResponsibilityRepository;
import com.darya.jobassistant.careerhistory.repository.CareerProjectAchievementRepository;
import com.darya.jobassistant.careerhistory.repository.CareerProjectRepository;
import com.darya.jobassistant.careerhistory.repository.CareerProjectResponsibilityRepository;
import com.darya.jobassistant.careerhistory.repository.CareerProjectTechnologyRepository;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessResourceException;

/**
 * Sprint 9 Step 7 correction: proves {@link CareerHistoryRepositoryAdapter#save}'s update path
 * translates <em>only</em> the actual PostgreSQL {@code 40001 serialization_failure} SQLSTATE into
 * {@link CareerHistoryConcurrentModificationException} - never by exception class alone. Built
 * entirely with Mockito against the plain Spring Data {@link CareerHistoryRepository} interface -
 * the smallest seam that lets every branch of {@code updateRoot}'s catch block be exercised
 * without a real database, since {@link org.springframework.dao.CannotAcquireLockException} is
 * (per the adapter's own javadoc) exactly the Spring exception class Hibernate surfaces for
 * <em>both</em> a genuine serialization failure and an unrelated lock timeout - only the
 * underlying {@link SQLException#getSQLState()} tells them apart.
 */
class CareerHistoryRepositoryAdapterConcurrencyTranslationTest {

    private final CareerHistoryRepository careerHistoryRepository = mock(CareerHistoryRepository.class);
    private final CareerHistoryRepositoryAdapter adapter = new CareerHistoryRepositoryAdapter(
            careerHistoryRepository,
            mock(CareerCompanyRepository.class),
            mock(CareerPositionRepository.class),
            mock(CareerPositionResponsibilityRepository.class),
            mock(CareerPositionAchievementRepository.class),
            mock(CareerProjectRepository.class),
            mock(CareerProjectResponsibilityRepository.class),
            mock(CareerProjectAchievementRepository.class),
            mock(CareerProjectTechnologyRepository.class),
            mock(CandidateProfileRepository.class),
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
            mock(EntityManager.class));

    private final CareerHistoryAggregate existingAggregate =
            new CareerHistoryAggregate(UUID.randomUUID(), UUID.randomUUID(), List.of(), 3L);

    @Test
    void serializationFailureSqlState_translatesToConcurrentModification() {
        when(careerHistoryRepository.updateVersionIfMatches(any(), any(), any(), anyLong()))
                .thenThrow(cannotAcquireLockException("40001"));

        assertThatThrownBy(() -> adapter.save(existingAggregate))
                .isInstanceOf(CareerHistoryConcurrentModificationException.class);
    }

    @Test
    void zeroUpdatedRows_stillTranslatesToConcurrentModification() {
        when(careerHistoryRepository.updateVersionIfMatches(any(), any(), any(), anyLong())).thenReturn(0);

        assertThatThrownBy(() -> adapter.save(existingAggregate))
                .isInstanceOf(CareerHistoryConcurrentModificationException.class);
    }

    /**
     * Same Spring exception class as the real serialization failure ({@link
     * CannotAcquireLockException} - see class javadoc), but a genuine lock-not-available SQLSTATE
     * ({@code 55P03}), not a serialization failure. Must propagate unchanged, not be
     * misclassified as an import conflict.
     */
    @Test
    void lockNotAvailableSqlState_propagatesUnchanged_notTranslated() {
        when(careerHistoryRepository.updateVersionIfMatches(any(), any(), any(), anyLong()))
                .thenThrow(cannotAcquireLockException("55P03"));

        assertThatThrownBy(() -> adapter.save(existingAggregate)).isInstanceOf(CannotAcquireLockException.class);
    }

    @Test
    void deadlockDetectedSqlState_propagatesUnchanged_notTranslated() {
        SQLException deadlock = new SQLException("deadlock detected", "40P01");
        when(careerHistoryRepository.updateVersionIfMatches(any(), any(), any(), anyLong()))
                .thenThrow(new PessimisticLockingFailureException("deadlock", deadlock));

        assertThatThrownBy(() -> adapter.save(existingAggregate)).isInstanceOf(PessimisticLockingFailureException.class);
    }

    @Test
    void genericDataAccessException_withNoSqlCause_propagatesUnchanged() {
        when(careerHistoryRepository.updateVersionIfMatches(any(), any(), any(), anyLong()))
                .thenThrow(new TransientDataAccessResourceException("connection reset"));

        assertThatThrownBy(() -> adapter.save(existingAggregate)).isInstanceOf(TransientDataAccessResourceException.class);
    }

    /** Confirms the two SQLSTATEs this test class relies on are actually distinct - see class javadoc. */
    @Test
    void serializationFailureAndLockNotAvailable_areDistinctSqlStates() {
        assertThat("40001").isNotEqualTo("55P03");
    }

    private CannotAcquireLockException cannotAcquireLockException(String sqlState) {
        SQLException sqlException = new SQLException("simulated", sqlState);
        return new CannotAcquireLockException("simulated", sqlException);
    }
}

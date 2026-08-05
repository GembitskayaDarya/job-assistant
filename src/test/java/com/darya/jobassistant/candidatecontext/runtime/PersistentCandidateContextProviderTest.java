package com.darya.jobassistant.candidatecontext.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileRepositoryPort;
import com.darya.jobassistant.candidates.runtime.CandidateProfileNotConfiguredException;
import com.darya.jobassistant.candidates.runtime.CandidateProfileRuntimeProperties;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersistentCandidateContextProviderTest {

    @Mock
    private CandidateProfileRepositoryPort candidateProfileRepositoryPort;

    @Mock
    private CareerHistoryRepositoryPort careerHistoryRepositoryPort;

    private PersistentCandidateContextProvider provider(String profileKey) {
        return new PersistentCandidateContextProvider(
                candidateProfileRepositoryPort, careerHistoryRepositoryPort, new CandidateProfileRuntimeProperties(profileKey));
    }

    @Test
    void loadCurrentContext_noCareerHistory_returnsSnapshotWithNotProvidedAvailability() {
        UUID candidateProfileId = UUID.randomUUID();
        CandidateProfileAggregate aggregate = aggregate(candidateProfileId, "primary", 3L);
        when(candidateProfileRepositoryPort.findByProfileKey("primary")).thenReturn(Optional.of(aggregate));
        when(careerHistoryRepositoryPort.findByCandidateProfileId(candidateProfileId)).thenReturn(Optional.empty());

        CandidateContextSnapshot snapshot = provider("primary").loadCurrentContext();

        assertThat(snapshot.candidateProfileId()).isEqualTo(candidateProfileId);
        assertThat(snapshot.profileKey()).isEqualTo("primary");
        assertThat(snapshot.candidateProfileVersion()).isEqualTo(3L);
        assertThat(snapshot.careerHistory()).isEmpty();
        assertThat(snapshot.careerHistoryAvailability()).isEqualTo(CareerHistoryAvailability.NOT_PROVIDED);
    }

    @Test
    void loadCurrentContext_careerHistoryWithNoCompanies_returnsEmptyAvailability() {
        UUID candidateProfileId = UUID.randomUUID();
        CandidateProfileAggregate aggregate = aggregate(candidateProfileId, "primary", 0L);
        CareerHistoryAggregate emptyHistory = new CareerHistoryAggregate(UUID.randomUUID(), candidateProfileId, List.of(), 5L);
        when(candidateProfileRepositoryPort.findByProfileKey("primary")).thenReturn(Optional.of(aggregate));
        when(careerHistoryRepositoryPort.findByCandidateProfileId(candidateProfileId)).thenReturn(Optional.of(emptyHistory));

        CandidateContextSnapshot snapshot = provider("primary").loadCurrentContext();

        assertThat(snapshot.careerHistoryAvailability()).isEqualTo(CareerHistoryAvailability.EMPTY);
        assertThat(snapshot.careerHistory()).contains(emptyHistory);
    }

    @Test
    void loadCurrentContext_careerHistoryWithCompanies_returnsAvailableAvailability() {
        UUID candidateProfileId = UUID.randomUUID();
        CandidateProfileAggregate aggregate = aggregate(candidateProfileId, "primary", 0L);
        CareerCompany company = new CareerCompany(null, "Acme", null, null, null, null, 0, List.of());
        CareerHistoryAggregate history =
                new CareerHistoryAggregate(UUID.randomUUID(), candidateProfileId, List.of(company), 2L);
        when(candidateProfileRepositoryPort.findByProfileKey("primary")).thenReturn(Optional.of(aggregate));
        when(careerHistoryRepositoryPort.findByCandidateProfileId(candidateProfileId)).thenReturn(Optional.of(history));

        CandidateContextSnapshot snapshot = provider("primary").loadCurrentContext();

        assertThat(snapshot.careerHistoryAvailability()).isEqualTo(CareerHistoryAvailability.AVAILABLE);
    }

    @Test
    void loadCurrentContext_missingCandidateProfile_throwsCandidateProfileNotConfiguredExceptionWithRequestedKey() {
        when(candidateProfileRepositoryPort.findByProfileKey("primary")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider("primary").loadCurrentContext())
                .isInstanceOf(CandidateProfileNotConfiguredException.class)
                .hasMessageContaining("primary");
        verifyNoInteractions(careerHistoryRepositoryPort);
    }

    @Test
    void loadCurrentContext_usesCandidateProfileAggregateTechnicalIdToLoadCareerHistory_notTheProfileKey() {
        UUID candidateProfileId = UUID.randomUUID();
        CandidateProfileAggregate aggregate = aggregate(candidateProfileId, "primary", 0L);
        when(candidateProfileRepositoryPort.findByProfileKey("primary")).thenReturn(Optional.of(aggregate));
        when(careerHistoryRepositoryPort.findByCandidateProfileId(any())).thenReturn(Optional.empty());

        provider("primary").loadCurrentContext();

        verify(careerHistoryRepositoryPort).findByCandidateProfileId(eq(candidateProfileId));
    }

    @Test
    void loadCurrentContext_neverCreatesCareerHistory() {
        UUID candidateProfileId = UUID.randomUUID();
        CandidateProfileAggregate aggregate = aggregate(candidateProfileId, "primary", 0L);
        when(candidateProfileRepositoryPort.findByProfileKey("primary")).thenReturn(Optional.of(aggregate));
        when(careerHistoryRepositoryPort.findByCandidateProfileId(candidateProfileId)).thenReturn(Optional.empty());

        provider("primary").loadCurrentContext();

        verify(careerHistoryRepositoryPort, never()).save(any());
    }

    private CandidateProfileAggregate aggregate(UUID id, String profileKey, long version) {
        return new CandidateProfileAggregate(
                id, profileKey, "Senior Java Backend Engineer", "Senior", 6,
                null, null, null, null, null, null,
                null, false, null, List.of(), List.of(), List.of(), version);
    }
}

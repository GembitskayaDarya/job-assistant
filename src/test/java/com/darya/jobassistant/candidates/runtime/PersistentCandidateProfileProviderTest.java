package com.darya.jobassistant.candidates.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Sprint 9 Step 4: unit-level coverage for {@link PersistentCandidateProfileProvider} against a
 * mocked {@link CandidateProfileRepositoryPort} - {@code PersistentCandidateProfileProviderIntegrationTest}
 * covers the same behaviors against real PostgreSQL.
 */
class PersistentCandidateProfileProviderTest {

    @Test
    void getProfile_existingAggregate_assemblesToAnalysisProfile() {
        CandidateProfileRepositoryPort repositoryPort = mock(CandidateProfileRepositoryPort.class);
        when(repositoryPort.findByProfileKey("primary")).thenReturn(Optional.of(sampleAggregate()));
        PersistentCandidateProfileProvider provider =
                new PersistentCandidateProfileProvider(repositoryPort, new CandidateProfileRuntimeProperties("primary"));

        CandidateProfile profile = provider.getProfile();

        assertThat(profile.targetRole()).isEqualTo("Senior Java Backend Engineer");
        assertThat(profile.experienceYears()).isEqualTo(6);
    }

    @Test
    void getProfile_missingAggregate_throwsCandidateProfileNotConfiguredException_neverReturnsNullOrEmptyDefault() {
        CandidateProfileRepositoryPort repositoryPort = mock(CandidateProfileRepositoryPort.class);
        when(repositoryPort.findByProfileKey("primary")).thenReturn(Optional.empty());
        PersistentCandidateProfileProvider provider =
                new PersistentCandidateProfileProvider(repositoryPort, new CandidateProfileRuntimeProperties("primary"));

        assertThatThrownBy(provider::getProfile)
                .isInstanceOf(CandidateProfileNotConfiguredException.class)
                .hasMessageContaining("primary");
    }

    @Test
    void getProfile_usesConfiguredProfileKey_notHardcodedPrimary() {
        CandidateProfileRepositoryPort repositoryPort = mock(CandidateProfileRepositoryPort.class);
        when(repositoryPort.findByProfileKey("secondary")).thenReturn(Optional.of(sampleAggregate()));
        PersistentCandidateProfileProvider provider =
                new PersistentCandidateProfileProvider(repositoryPort, new CandidateProfileRuntimeProperties("secondary"));

        provider.getProfile();

        verify(repositoryPort, times(1)).findByProfileKey("secondary");
    }

    @Test
    void getProfile_doesNotCache_reflectsADifferentResultOnTheNextCall() {
        CandidateProfileRepositoryPort repositoryPort = mock(CandidateProfileRepositoryPort.class);
        when(repositoryPort.findByProfileKey("primary"))
                .thenReturn(Optional.of(sampleAggregate()))
                .thenReturn(Optional.of(aggregateWithRole("Staff Backend Engineer")));
        PersistentCandidateProfileProvider provider =
                new PersistentCandidateProfileProvider(repositoryPort, new CandidateProfileRuntimeProperties("primary"));

        assertThat(provider.getProfile().targetRole()).isEqualTo("Senior Java Backend Engineer");
        assertThat(provider.getProfile().targetRole()).isEqualTo("Staff Backend Engineer");
        verify(repositoryPort, times(2)).findByProfileKey("primary");
    }

    @Test
    void getProfile_persistenceFailure_propagatesUnchanged_neverConvertedToYamlFallback() {
        CandidateProfileRepositoryPort repositoryPort = mock(CandidateProfileRepositoryPort.class);
        RuntimeException persistenceFailure = new RuntimeException("connection reset");
        when(repositoryPort.findByProfileKey("primary")).thenThrow(persistenceFailure);
        PersistentCandidateProfileProvider provider =
                new PersistentCandidateProfileProvider(repositoryPort, new CandidateProfileRuntimeProperties("primary"));

        assertThatThrownBy(provider::getProfile).isSameAs(persistenceFailure);
    }

    private CandidateProfileAggregate sampleAggregate() {
        return aggregateWithRole("Senior Java Backend Engineer");
    }

    private CandidateProfileAggregate aggregateWithRole(String targetRole) {
        return new CandidateProfileAggregate(
                UUID.randomUUID(), "primary", targetRole, "Senior", 6,
                null, null, null, null, null, null,
                null, false, null, List.of(), List.of(), List.of(), 0L);
    }
}

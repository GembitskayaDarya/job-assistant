package com.darya.jobassistant.candidates.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateProfileProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;

/**
 * Sprint 9 Step 4 correction: proves {@link CandidateProfileStartupValidator} - now an {@link
 * ApplicationRunner}, not an {@code ApplicationReadyEvent} listener - calls the real runtime
 * provider exactly once, performs no write, and lets any failure (missing profile or an invalid
 * aggregate-to-analysis assembly) propagate completely uncaught rather than swallowing or
 * re-logging it - a rolled-back/broken startup must never look like a quiet success. {@code
 * CandidateProfileStartupValidatorLifecycleTest} additionally proves this against a real {@code
 * SpringApplication} boot, showing the failure happens strictly before {@code
 * ApplicationReadyEvent} is published.
 */
class CandidateProfileStartupValidatorTest {

    @Test
    void implementsApplicationRunner() {
        assertThat(ApplicationRunner.class.isAssignableFrom(CandidateProfileStartupValidator.class)).isTrue();
    }

    @Test
    void run_profileExists_succeeds_callsProviderExactlyOnceAndNothingElse() {
        CandidateProfileProvider provider = mock(CandidateProfileProvider.class);
        when(provider.getProfile()).thenReturn(sampleProfile());
        CandidateProfileStartupValidator validator =
                new CandidateProfileStartupValidator(provider, new CandidateProfileRuntimeProperties("primary"));

        assertThatCode(() -> validator.run(new DefaultApplicationArguments())).doesNotThrowAnyException();

        verify(provider, times(1)).getProfile();
        verifyNoMoreInteractions(provider);
    }

    @Test
    void run_missingProfile_propagatesCandidateProfileNotConfiguredExceptionUncaught() {
        CandidateProfileProvider provider = mock(CandidateProfileProvider.class);
        when(provider.getProfile()).thenThrow(new CandidateProfileNotConfiguredException("primary"));
        CandidateProfileStartupValidator validator =
                new CandidateProfileStartupValidator(provider, new CandidateProfileRuntimeProperties("primary"));

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isInstanceOf(CandidateProfileNotConfiguredException.class);
    }

    @Test
    void run_invalidAssembly_propagatesOriginalFailureUncaught() {
        CandidateProfileProvider provider = mock(CandidateProfileProvider.class);
        IllegalArgumentException assemblyFailure = new IllegalArgumentException("assembly failed");
        when(provider.getProfile()).thenThrow(assemblyFailure);
        CandidateProfileStartupValidator validator =
                new CandidateProfileStartupValidator(provider, new CandidateProfileRuntimeProperties("primary"));

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments())).isSameAs(assemblyFailure);
    }

    private CandidateProfile sampleProfile() {
        CandidatePreferences preferences = new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null);
        return new CandidateProfile("Senior Java Backend Engineer", "Senior", List.of(), List.of(), 6, preferences);
    }
}

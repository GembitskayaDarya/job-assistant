package com.darya.jobassistant.candidatecontext.applicationmaterials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGeneration;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationStatus;
import com.darya.jobassistant.candidatecontext.CandidateContextProvider;
import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextForApplicationMaterials;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextForApplicationMaterialsSelectionMetadata;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextVersionMismatchException;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextVersionMismatchException.Reason;
import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplicationMaterialsCandidateContextProviderTest {

    @Mock
    private CandidateContextProvider candidateContextProvider;

    @Mock
    private CandidateContextForApplicationMaterialsSelector selector;

    private final UUID vacancyId = UUID.randomUUID();
    private final Instant requestedAt = Instant.parse("2026-01-01T00:00:00Z");
    private final JobOffer vacancy = new JobOffer("job-1", "Backend Engineer", "Acme", "Remote", null, "desc", "https://example.com", "test");

    private ApplicationMaterialsCandidateContextProvider provider() {
        return new ApplicationMaterialsCandidateContextProvider(candidateContextProvider, selector);
    }

    // ==================== Matching versions succeed and delegate to the selector ====================

    @Test
    void loadContext_noCareerHistoryExpectedOrPresent_delegatesToSelector() {
        ApplicationMaterialGeneration generation = generation(3L, null);
        CandidateContextSnapshot snapshot = snapshot(3L, Optional.empty());
        CandidateContextForApplicationMaterials expected = emptyContext();
        when(candidateContextProvider.loadCurrentContext()).thenReturn(snapshot);
        when(selector.select(snapshot, vacancy)).thenReturn(expected);

        CandidateContextForApplicationMaterials result = provider().loadContext(generation, vacancy);

        assertThat(result).isSameAs(expected);
        verify(selector).select(snapshot, vacancy);
    }

    @Test
    void loadContext_careerHistoryExpectedAndPresentAtSameVersion_delegatesToSelector() {
        ApplicationMaterialGeneration generation = generation(3L, 7L);
        CareerHistoryAggregate careerHistory = careerHistory(7L);
        CandidateContextSnapshot snapshot = snapshot(3L, Optional.of(careerHistory));
        CandidateContextForApplicationMaterials expected = emptyContext();
        when(candidateContextProvider.loadCurrentContext()).thenReturn(snapshot);
        when(selector.select(snapshot, vacancy)).thenReturn(expected);

        CandidateContextForApplicationMaterials result = provider().loadContext(generation, vacancy);

        assertThat(result).isSameAs(expected);
    }

    // ==================== Candidate Profile version mismatch ====================

    @Test
    void loadContext_candidateProfileVersionChanged_throwsWithoutCallingSelector() {
        ApplicationMaterialGeneration generation = generation(3L, null);
        CandidateContextSnapshot snapshot = snapshot(4L, Optional.empty());
        when(candidateContextProvider.loadCurrentContext()).thenReturn(snapshot);

        assertThatThrownBy(() -> provider().loadContext(generation, vacancy))
                .isInstanceOf(CandidateContextVersionMismatchException.class)
                .extracting(ex -> ((CandidateContextVersionMismatchException) ex).reason())
                .isEqualTo(Reason.CANDIDATE_PROFILE_VERSION_CHANGED);
        verifyNoInteractions(selector);
    }

    @Test
    void loadContext_candidateProfileVersionChanged_exceptionCarriesExpectedAndActualVersions() {
        ApplicationMaterialGeneration generation = generation(3L, null);
        CandidateContextSnapshot snapshot = snapshot(4L, Optional.empty());
        when(candidateContextProvider.loadCurrentContext()).thenReturn(snapshot);

        CandidateContextVersionMismatchException exception = (CandidateContextVersionMismatchException)
                catchThrowable(() -> provider().loadContext(generation, vacancy));

        assertThat(exception.generationId()).isEqualTo(generation.id());
        assertThat(exception.expectedCandidateProfileVersion()).isEqualTo(3L);
        assertThat(exception.actualCandidateProfileVersion()).isEqualTo(4L);
    }

    // ==================== Career History now absent ====================

    @Test
    void loadContext_careerHistoryExpectedButNowAbsent_throwsCareerHistoryNowAbsent() {
        ApplicationMaterialGeneration generation = generation(3L, 7L);
        CandidateContextSnapshot snapshot = snapshot(3L, Optional.empty());
        when(candidateContextProvider.loadCurrentContext()).thenReturn(snapshot);

        assertThatThrownBy(() -> provider().loadContext(generation, vacancy))
                .isInstanceOf(CandidateContextVersionMismatchException.class)
                .extracting(ex -> ((CandidateContextVersionMismatchException) ex).reason())
                .isEqualTo(Reason.CAREER_HISTORY_NOW_ABSENT);
        verifyNoInteractions(selector);
    }

    // ==================== Career History now present ====================

    @Test
    void loadContext_careerHistoryNotExpectedButNowPresent_throwsCareerHistoryNowPresent() {
        ApplicationMaterialGeneration generation = generation(3L, null);
        CandidateContextSnapshot snapshot = snapshot(3L, Optional.of(careerHistory(0L)));
        when(candidateContextProvider.loadCurrentContext()).thenReturn(snapshot);

        assertThatThrownBy(() -> provider().loadContext(generation, vacancy))
                .isInstanceOf(CandidateContextVersionMismatchException.class)
                .extracting(ex -> ((CandidateContextVersionMismatchException) ex).reason())
                .isEqualTo(Reason.CAREER_HISTORY_NOW_PRESENT);
        verifyNoInteractions(selector);
    }

    // ==================== Career History version changed ====================

    @Test
    void loadContext_careerHistoryVersionChanged_throwsCareerHistoryVersionChanged() {
        ApplicationMaterialGeneration generation = generation(3L, 7L);
        CandidateContextSnapshot snapshot = snapshot(3L, Optional.of(careerHistory(8L)));
        when(candidateContextProvider.loadCurrentContext()).thenReturn(snapshot);

        assertThatThrownBy(() -> provider().loadContext(generation, vacancy))
                .isInstanceOf(CandidateContextVersionMismatchException.class)
                .extracting(ex -> ((CandidateContextVersionMismatchException) ex).reason())
                .isEqualTo(Reason.CAREER_HISTORY_VERSION_CHANGED);
        verifyNoInteractions(selector);
    }

    @Test
    void loadContext_careerHistoryVersionChanged_exceptionCarriesExpectedAndActualCareerHistoryVersions() {
        ApplicationMaterialGeneration generation = generation(3L, 7L);
        CandidateContextSnapshot snapshot = snapshot(3L, Optional.of(careerHistory(8L)));
        when(candidateContextProvider.loadCurrentContext()).thenReturn(snapshot);

        CandidateContextVersionMismatchException exception = (CandidateContextVersionMismatchException)
                catchThrowable(() -> provider().loadContext(generation, vacancy));

        assertThat(exception.expectedCareerHistoryVersion()).isEqualTo(7L);
        assertThat(exception.actualCareerHistoryVersion()).isEqualTo(8L);
    }

    // ==================== Priority order: candidate profile checked before career history ====================

    @Test
    void loadContext_bothCandidateProfileAndCareerHistoryChanged_reportsCandidateProfileFirst() {
        ApplicationMaterialGeneration generation = generation(3L, 7L);
        CandidateContextSnapshot snapshot = snapshot(4L, Optional.of(careerHistory(8L)));
        when(candidateContextProvider.loadCurrentContext()).thenReturn(snapshot);

        assertThatThrownBy(() -> provider().loadContext(generation, vacancy))
                .isInstanceOf(CandidateContextVersionMismatchException.class)
                .extracting(ex -> ((CandidateContextVersionMismatchException) ex).reason())
                .isEqualTo(Reason.CANDIDATE_PROFILE_VERSION_CHANGED);
        verify(candidateContextProvider).loadCurrentContext();
        verifyNoInteractions(selector);
    }

    // ==================== Fixtures ====================

    private ApplicationMaterialGeneration generation(long candidateProfileVersion, Long careerHistoryVersion) {
        return new ApplicationMaterialGeneration(
                UUID.randomUUID(), vacancyId, ApplicationMaterialGenerationStatus.PENDING,
                candidateProfileVersion, careerHistoryVersion, requestedAt, null, null, null, null, 0L);
    }

    private CandidateContextSnapshot snapshot(long candidateProfileVersion, Optional<CareerHistoryAggregate> careerHistory) {
        return new CandidateContextSnapshot(UUID.randomUUID(), "primary", candidateProfileVersion, validProfile(), careerHistory);
    }

    private CareerHistoryAggregate careerHistory(long version) {
        return new CareerHistoryAggregate(UUID.randomUUID(), UUID.randomUUID(), List.<CareerCompany>of(), version);
    }

    private CandidateProfile validProfile() {
        return new CandidateProfile("Senior Java Backend Engineer", "Senior", List.of(), List.of(), 5,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null));
    }

    private CandidateContextForApplicationMaterials emptyContext() {
        return new CandidateContextForApplicationMaterials(validProfile(), CareerHistoryAvailability.NOT_PROVIDED, List.of(),
                CandidateContextForApplicationMaterialsSelectionMetadata.empty(CareerHistoryAvailability.NOT_PROVIDED, null));
    }
}

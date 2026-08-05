package com.darya.jobassistant.candidatecontext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidateContextSnapshotTest {

    @Test
    void constructor_validArguments_createsSnapshot() {
        CandidateContextSnapshot snapshot = new CandidateContextSnapshot(
                UUID.randomUUID(), "primary", 3L, validProfile(), Optional.empty());

        assertThat(snapshot.profileKey()).isEqualTo("primary");
        assertThat(snapshot.candidateProfileVersion()).isEqualTo(3L);
    }

    @Test
    void constructor_nullCandidateProfileId_isRejected() {
        assertThatThrownBy(() -> new CandidateContextSnapshot(null, "primary", 0L, validProfile(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_blankProfileKey_isRejected() {
        assertThatThrownBy(() -> new CandidateContextSnapshot(UUID.randomUUID(), "  ", 0L, validProfile(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negativeVersion_isRejected() {
        assertThatThrownBy(() -> new CandidateContextSnapshot(UUID.randomUUID(), "primary", -1L, validProfile(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullCandidateProfile_isRejected() {
        assertThatThrownBy(() -> new CandidateContextSnapshot(UUID.randomUUID(), "primary", 0L, null, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullCareerHistoryOptional_becomesEmpty() {
        CandidateContextSnapshot snapshot = new CandidateContextSnapshot(UUID.randomUUID(), "primary", 0L, validProfile(), null);

        assertThat(snapshot.careerHistory()).isEmpty();
    }

    @Test
    void careerHistoryAvailability_noCareerHistory_isNotProvided() {
        CandidateContextSnapshot snapshot = new CandidateContextSnapshot(
                UUID.randomUUID(), "primary", 0L, validProfile(), Optional.empty());

        assertThat(snapshot.careerHistoryAvailability()).isEqualTo(CareerHistoryAvailability.NOT_PROVIDED);
    }

    @Test
    void careerHistoryAvailability_careerHistoryWithNoCompanies_isEmpty() {
        UUID candidateProfileId = UUID.randomUUID();
        CareerHistoryAggregate careerHistory = new CareerHistoryAggregate(UUID.randomUUID(), candidateProfileId, List.of(), 0L);
        CandidateContextSnapshot snapshot = new CandidateContextSnapshot(
                candidateProfileId, "primary", 0L, validProfile(), Optional.of(careerHistory));

        assertThat(snapshot.careerHistoryAvailability()).isEqualTo(CareerHistoryAvailability.EMPTY);
    }

    @Test
    void careerHistoryAvailability_careerHistoryWithAtLeastOneCompany_isAvailable() {
        UUID candidateProfileId = UUID.randomUUID();
        CareerCompany company = new CareerCompany(null, "Example Systems", null, null, null, null, 0, List.of());
        CareerHistoryAggregate careerHistory =
                new CareerHistoryAggregate(UUID.randomUUID(), candidateProfileId, List.of(company), 0L);
        CandidateContextSnapshot snapshot = new CandidateContextSnapshot(
                candidateProfileId, "primary", 0L, validProfile(), Optional.of(careerHistory));

        assertThat(snapshot.careerHistoryAvailability()).isEqualTo(CareerHistoryAvailability.AVAILABLE);
    }

    private CandidateProfile validProfile() {
        return new CandidateProfile(
                "Senior Java Backend Engineer", "Senior", List.of(), List.of(), 5,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null));
    }
}

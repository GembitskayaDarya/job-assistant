package com.darya.jobassistant.candidatecontext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import java.lang.reflect.RecordComponent;
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

    /**
     * Sprint 11 Step 1 correction regression guard: {@link CandidateContextSnapshot#candidateProfile()}
     * must carry the complete {@link CandidateProfileFacts}, never the vacancy-analysis-bounded
     * {@link CandidateProfile} - a common context built from the lossy type would silently drop
     * skill category and language proficiency before any non-analysis downstream consumer (e.g.
     * {@code CvSourceSnapshotFactory}) ever saw them. Enforced structurally by the compiler already
     * (the record component type itself is {@link CandidateProfileFacts}), asserted here via
     * reflection so a future revert is caught by this test even if some future call site were to
     * compile against a loosened signature.
     */
    @Test
    void candidateProfileComponent_isTheCompleteFactsType_notTheAnalysisBoundedProfile() {
        RecordComponent[] components = CandidateContextSnapshot.class.getRecordComponents();
        RecordComponent candidateProfileComponent = List.of(components).stream()
                .filter(component -> component.getName().equals("candidateProfile"))
                .findFirst()
                .orElseThrow();

        assertThat(candidateProfileComponent.getType()).isEqualTo(CandidateProfileFacts.class);
        assertThat(candidateProfileComponent.getType()).isNotEqualTo(CandidateProfile.class);
    }

    // ---- Sprint 11 Step 5: Personal Projects ----

    @Test
    void constructor_fiveArgOverload_defaultsPersonalProjectsToEmpty() {
        CandidateContextSnapshot snapshot = new CandidateContextSnapshot(
                UUID.randomUUID(), "primary", 0L, validProfile(), Optional.empty());

        assertThat(snapshot.personalProjects()).isEmpty();
    }

    @Test
    void constructor_nullPersonalProjects_becomesEmpty() {
        CandidateContextSnapshot snapshot = new CandidateContextSnapshot(
                UUID.randomUUID(), "primary", 0L, validProfile(), Optional.empty(), null);

        assertThat(snapshot.personalProjects()).isEmpty();
    }

    @Test
    void constructor_suppliedPersonalProjects_arePreserved() {
        UUID candidateProfileId = UUID.randomUUID();
        com.darya.jobassistant.personalprojects.aggregate.PersonalProject project =
                new com.darya.jobassistant.personalprojects.aggregate.PersonalProject(
                        candidateProfileId, "Example Project", null, null, null, null, 0, List.of(), List.of());
        CandidateContextSnapshot snapshot = new CandidateContextSnapshot(
                candidateProfileId, "primary", 0L, validProfile(), Optional.empty(), List.of(project));

        assertThat(snapshot.personalProjects()).containsExactly(project);
    }

    private CandidateProfileFacts validProfile() {
        return new CandidateProfileFacts(
                "Senior Java Backend Engineer", "Senior", List.of(), List.of(), 5,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null));
    }
}

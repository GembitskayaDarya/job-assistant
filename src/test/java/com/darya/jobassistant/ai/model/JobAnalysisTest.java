package com.darya.jobassistant.ai.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobAnalysisTest {

    private final List<String> pros = List.of("Strong Java skills");
    private final List<String> cons = List.of("No Kafka experience");
    private final List<String> missingRequiredSkills = List.of("Kafka");
    private final List<String> missingPreferredSkills = List.of("Terraform");
    private final String experienceAssessment = "6 years vs. no stated requirement.";
    private final String preferencesAssessment = "Remote preference matches.";

    @Test
    void constructor_validAnalysis_isCreated() {
        JobAnalysis analysis = new JobAnalysis(
                85, pros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment, "Good match");

        assertThat(analysis.score()).isEqualTo(85);
        assertThat(analysis.pros()).containsExactly("Strong Java skills");
        assertThat(analysis.cons()).containsExactly("No Kafka experience");
        assertThat(analysis.missingRequiredSkills()).containsExactly("Kafka");
        assertThat(analysis.missingPreferredSkills()).containsExactly("Terraform");
        assertThat(analysis.experienceAssessment()).isEqualTo(experienceAssessment);
        assertThat(analysis.preferencesAssessment()).isEqualTo(preferencesAssessment);
        assertThat(analysis.summary()).isEqualTo("Good match");
    }

    @Test
    void constructor_scoreBelowZero_isRejected() {
        assertThatThrownBy(() -> new JobAnalysis(
                -1, pros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment, "Good match"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_scoreAboveHundred_isRejected() {
        assertThatThrownBy(() -> new JobAnalysis(
                101, pros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment, "Good match"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_acceptsBoundaryScores() {
        assertThat(new JobAnalysis(
                0, pros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment, "Good match").score()).isZero();
        assertThat(new JobAnalysis(
                100, pros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment, "Good match").score()).isEqualTo(100);
    }

    @Test
    void constructor_blankSummary_isRejected() {
        assertThatThrownBy(() -> new JobAnalysis(
                85, pros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullSummary_isRejected() {
        assertThatThrownBy(() -> new JobAnalysis(
                85, pros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_blankExperienceAssessment_isRejected() {
        assertThatThrownBy(() -> new JobAnalysis(
                85, pros, cons, missingRequiredSkills, missingPreferredSkills,
                "   ", preferencesAssessment, "Good match"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullExperienceAssessment_isRejected() {
        assertThatThrownBy(() -> new JobAnalysis(
                85, pros, cons, missingRequiredSkills, missingPreferredSkills,
                null, preferencesAssessment, "Good match"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_blankPreferencesAssessment_isRejected() {
        assertThatThrownBy(() -> new JobAnalysis(
                85, pros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, "   ", "Good match"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullPreferencesAssessment_isRejected() {
        assertThatThrownBy(() -> new JobAnalysis(
                85, pros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, null, "Good match"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullLists_normalizeToEmptyLists() {
        JobAnalysis analysis = new JobAnalysis(
                85, null, null, null, null, experienceAssessment, preferencesAssessment, "Good match");

        assertThat(analysis.pros()).isEmpty();
        assertThat(analysis.cons()).isEmpty();
        assertThat(analysis.missingRequiredSkills()).isEmpty();
        assertThat(analysis.missingPreferredSkills()).isEmpty();
    }

    @Test
    void constructor_mutatingSourceLists_doesNotAffectStoredState() {
        List<String> mutablePros = new ArrayList<>(pros);

        JobAnalysis analysis = new JobAnalysis(
                85, mutablePros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment, "Good match");
        mutablePros.add("Extra");

        assertThat(analysis.pros()).containsExactly("Strong Java skills");
    }

    @Test
    void accessors_listsAreUnmodifiable() {
        JobAnalysis analysis = new JobAnalysis(
                85, pros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment, "Good match");

        assertThatThrownBy(() -> analysis.pros().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> analysis.cons().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> analysis.missingRequiredSkills().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> analysis.missingPreferredSkills().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }
}

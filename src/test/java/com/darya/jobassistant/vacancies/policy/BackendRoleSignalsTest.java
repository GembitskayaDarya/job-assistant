package com.darya.jobassistant.vacancies.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class BackendRoleSignalsTest {

    @Test
    void javaBackendEngineerTitle_matches() {
        assertThat(BackendRoleSignals.matches("Senior Java Backend Engineer", "We use Spring Boot and Kafka.", List.of()))
                .isTrue();
    }

    @Test
    void frontendTitle_neverMatchesRegardlessOfDescription() {
        assertThat(BackendRoleSignals.matches("Frontend Engineer", "Java Spring Boot backend team", List.of())).isFalse();
    }

    @Test
    void nonEngineeringTitle_doesNotMatch() {
        assertThat(BackendRoleSignals.matches("Sales Manager", "Java Spring Boot backend", List.of())).isFalse();
    }

    @Test
    void engineeringTitleWithoutJavaSignal_doesNotMatch() {
        assertThat(BackendRoleSignals.matches("Backend Engineer", "We use Python and Django.", List.of())).isFalse();
    }

    @Test
    void javaSignalInTagsOnly_stillMatches() {
        assertThat(BackendRoleSignals.matches("Software Engineer", "Server-side role.", List.of("Java", "Spring"))).isTrue();
    }

    @Test
    void hasExcludedTitleSignal_detectsNonBackendDisciplines() {
        assertThat(BackendRoleSignals.hasExcludedTitleSignal("Frontend Developer")).isTrue();
        assertThat(BackendRoleSignals.hasExcludedTitleSignal("QA Engineer")).isTrue();
        assertThat(BackendRoleSignals.hasExcludedTitleSignal("Senior Recruiter")).isTrue();
    }

    @Test
    void hasExcludedTitleSignal_neverFlagsAGenuineBackendTitle() {
        assertThat(BackendRoleSignals.hasExcludedTitleSignal("Senior Java Backend Engineer")).isFalse();
    }

    @Test
    void hasExcludedTitleSignal_isConservativeAboutSparseTitles() {
        // A short, ambiguous title (no positive OR negative signal) must never be flagged - only
        // an explicit negative signal counts, matching the cheap/early check's conservative intent.
        assertThat(BackendRoleSignals.hasExcludedTitleSignal("Engineer")).isFalse();
        assertThat(BackendRoleSignals.hasExcludedTitleSignal(null)).isFalse();
    }
}

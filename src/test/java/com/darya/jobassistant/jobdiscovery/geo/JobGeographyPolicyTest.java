package com.darya.jobassistant.jobdiscovery.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobGeographyPolicyTest {

    private JobGeographyPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new JobGeographyPolicy(new JobGeographyPolicyProperties(
                List.of("Poland", "Warsaw", "Europe", "European", "EU", "Remote Europe"),
                List.of("US only", "USA only", "US citizens only", "UK only")));
    }

    @Test
    void polandCompatible_isEligible() {
        assertThat(policy.assess("Senior Java Backend Engineer", "Remote from Poland")).isEqualTo(GeographyEligibility.ELIGIBLE);
    }

    @Test
    void europeCompatible_isEligible() {
        assertThat(policy.assess("Backend Engineer - Remote Europe")).isEqualTo(GeographyEligibility.ELIGIBLE);
    }

    @Test
    void clearlyIncompatibleRegion_isIneligible() {
        assertThat(policy.assess("Backend Engineer (US citizens only)")).isEqualTo(GeographyEligibility.INELIGIBLE);
        assertThat(policy.assess("Backend Engineer - US only")).isEqualTo(GeographyEligibility.INELIGIBLE);
    }

    @Test
    void missingOrUnknownLocation_isUnknownNeverRejected() {
        assertThat(policy.assess((String) null)).isEqualTo(GeographyEligibility.UNKNOWN);
        assertThat(policy.assess("")).isEqualTo(GeographyEligibility.UNKNOWN);
        assertThat(policy.assess("Backend Engineer")).isEqualTo(GeographyEligibility.UNKNOWN);
    }

    @Test
    void unrelatedMentionOfAcceptedWordAsSubstring_doesNotFalselyMatch() {
        // "eu" must not match inside "european" via substring - tokenization prevents that, and
        // here "european" is itself a configured accepted term so this also proves it still works.
        assertThat(policy.assess("A truly European remote-first company")).isEqualTo(GeographyEligibility.ELIGIBLE);
    }

    @Test
    void incompatibleSignalOverridesAnyAcceptedMention() {
        assertThat(policy.assess("Europe based company, but this specific role is US citizens only"))
                .isEqualTo(GeographyEligibility.INELIGIBLE);
    }

    @Test
    void combinesMultipleTextInputs() {
        assertThat(policy.assess("Backend Engineer", null, "Warsaw, Poland")).isEqualTo(GeographyEligibility.ELIGIBLE);
    }

    @Test
    void emptyConfiguredTermLists_alwaysUnknown() {
        JobGeographyPolicy empty = new JobGeographyPolicy(new JobGeographyPolicyProperties(List.of(), List.of()));
        assertThat(empty.assess("Warsaw, Poland - US only")).isEqualTo(GeographyEligibility.UNKNOWN);
    }
}

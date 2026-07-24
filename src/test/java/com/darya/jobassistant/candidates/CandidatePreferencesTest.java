package com.darya.jobassistant.candidates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandidatePreferencesTest {

    @Test
    void constructor_validPreferences_areCreated() {
        CandidatePreferences preferences = new CandidatePreferences(
                "Poland",
                "Remote",
                PreferenceImportance.STRONG,
                List.of("Poland"),
                false,
                List.of("B2B"),
                PreferenceImportance.PREFERRED,
                "Product company",
                PreferenceImportance.PREFERRED,
                null);

        assertThat(preferences.currentCountry()).isEqualTo("Poland");
        assertThat(preferences.allowedWorkCountries()).containsExactly("Poland");
        assertThat(preferences.preferredContractTypes()).containsExactly("B2B");
        assertThat(preferences.relocationAllowed()).isFalse();
        assertThat(preferences.salaryExpectation()).isNull();
    }

    @Test
    void constructor_nullLists_becomeEmptyImmutableLists() {
        CandidatePreferences preferences = new CandidatePreferences(
                null, null, null, null, false, null, null, null, null, null);

        assertThat(preferences.allowedWorkCountries()).isEmpty();
        assertThat(preferences.preferredContractTypes()).isEmpty();
        assertThatThrownBy(() -> preferences.allowedWorkCountries().add("Poland"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> preferences.preferredContractTypes().add("B2B"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constructor_mutatingSourceListAfterConstruction_doesNotAffectStoredState() {
        List<String> countries = new ArrayList<>(List.of("Poland"));
        List<String> contractTypes = new ArrayList<>(List.of("B2B"));

        CandidatePreferences preferences = new CandidatePreferences(
                "Poland", "Remote", PreferenceImportance.STRONG, countries, false,
                contractTypes, PreferenceImportance.PREFERRED, null, null, null);

        countries.add("Germany");
        contractTypes.add("UoP");

        assertThat(preferences.allowedWorkCountries()).containsExactly("Poland");
        assertThat(preferences.preferredContractTypes()).containsExactly("B2B");
    }

    @Test
    void accessors_returnedLists_areUnmodifiable() {
        CandidatePreferences preferences = new CandidatePreferences(
                "Poland", "Remote", PreferenceImportance.STRONG, List.of("Poland"), false,
                List.of("B2B"), PreferenceImportance.PREFERRED, null, null, null);

        assertThatThrownBy(() -> preferences.allowedWorkCountries().add("Germany"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> preferences.preferredContractTypes().add("UoP"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

package com.darya.jobassistant.candidates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PersistedCandidateLanguageTest {

    @Test
    void constructor_validLanguage_isCreated() {
        PersistedCandidateLanguage language = new PersistedCandidateLanguage("en", "FLUENT");

        assertThat(language.languageCode()).isEqualTo("en");
        assertThat(language.proficiency()).isEqualTo("FLUENT");
    }

    @Test
    void constructor_nullProficiency_isAllowed() {
        PersistedCandidateLanguage language = new PersistedCandidateLanguage("pl", null);

        assertThat(language.proficiency()).isNull();
    }

    @Test
    void constructor_blankProficiency_becomesNull() {
        PersistedCandidateLanguage language = new PersistedCandidateLanguage("ru", "   ");

        assertThat(language.proficiency()).isNull();
    }

    @Test
    void constructor_nullLanguageCode_isRejected() {
        assertThatThrownBy(() -> new PersistedCandidateLanguage(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_blankLanguageCode_isRejected() {
        assertThatThrownBy(() -> new PersistedCandidateLanguage("   ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_uppercaseLanguageCode_isRejected() {
        assertThatThrownBy(() -> new PersistedCandidateLanguage("EN", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_languageCodeContainingDigits_isRejected() {
        assertThatThrownBy(() -> new PersistedCandidateLanguage("e1", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_validTwoAndThreeLetterCodes_areAccepted() {
        assertThat(new PersistedCandidateLanguage("en", null).languageCode()).isEqualTo("en");
        assertThat(new PersistedCandidateLanguage("rus", null).languageCode()).isEqualTo("rus");
    }
}

package com.darya.jobassistant.candidates.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CandidateLanguageTest {

    @Test
    void constructor_validLanguage_isCreated() {
        CandidateLanguage language = new CandidateLanguage("en", "FLUENT");

        assertThat(language.languageCode()).isEqualTo("en");
        assertThat(language.proficiency()).isEqualTo("FLUENT");
    }

    @Test
    void constructor_nullProficiency_isAllowed() {
        CandidateLanguage language = new CandidateLanguage("pl", null);

        assertThat(language.proficiency()).isNull();
    }

    @Test
    void constructor_blankProficiency_becomesNull() {
        CandidateLanguage language = new CandidateLanguage("ru", "   ");

        assertThat(language.proficiency()).isNull();
    }

    @Test
    void constructor_nullLanguageCode_isRejected() {
        assertThatThrownBy(() -> new CandidateLanguage(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_blankLanguageCode_isRejected() {
        assertThatThrownBy(() -> new CandidateLanguage("   ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_uppercaseLanguageCode_isRejected() {
        assertThatThrownBy(() -> new CandidateLanguage("EN", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_languageCodeContainingDigits_isRejected() {
        assertThatThrownBy(() -> new CandidateLanguage("e1", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_validTwoAndThreeLetterCodes_areAccepted() {
        assertThat(new CandidateLanguage("en", null).languageCode()).isEqualTo("en");
        assertThat(new CandidateLanguage("rus", null).languageCode()).isEqualTo("rus");
    }

    @Test
    void constructor_proficiencyLongerThanDatabaseColumn_isDomainValid() {
        // The database column (candidate_profile_language.proficiency VARCHAR(50)) is the sole
        // length authority - the domain intentionally does not duplicate that constraint. See
        // CandidateProfileRepositoryAdapterTest's public-port rollback test, which relies on this
        // being constructible to prove child-write failure atomicity through the real port.
        CandidateLanguage language = new CandidateLanguage("en", "A".repeat(51));

        assertThat(language.proficiency()).hasSize(51);
    }
}

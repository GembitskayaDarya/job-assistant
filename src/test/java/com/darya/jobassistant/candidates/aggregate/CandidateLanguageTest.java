package com.darya.jobassistant.candidates.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CandidateLanguageTest {

    @Test
    void constructor_validLanguage_isCreated() {
        CandidateLanguage language = new CandidateLanguage("en", "FLUENT", 0);

        assertThat(language.languageCode()).isEqualTo("en");
        assertThat(language.proficiency()).isEqualTo("FLUENT");
    }

    @Test
    void constructor_nullProficiency_isAllowed() {
        CandidateLanguage language = new CandidateLanguage("pl", null, 0);

        assertThat(language.proficiency()).isNull();
    }

    @Test
    void constructor_blankProficiency_becomesNull() {
        CandidateLanguage language = new CandidateLanguage("ru", "   ", 0);

        assertThat(language.proficiency()).isNull();
    }

    @Test
    void constructor_nullLanguageCode_isRejected() {
        assertThatThrownBy(() -> new CandidateLanguage(null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_blankLanguageCode_isRejected() {
        assertThatThrownBy(() -> new CandidateLanguage("   ", null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_uppercaseLanguageCode_isRejected() {
        assertThatThrownBy(() -> new CandidateLanguage("EN", null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_languageCodeContainingDigits_isRejected() {
        assertThatThrownBy(() -> new CandidateLanguage("e1", null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_validTwoAndThreeLetterCodes_areAccepted() {
        assertThat(new CandidateLanguage("en", null, 0).languageCode()).isEqualTo("en");
        assertThat(new CandidateLanguage("rus", null, 0).languageCode()).isEqualTo("rus");
    }

    /**
     * Sprint 11 Step 5 acceptance correction: there is deliberately no convenience constructor
     * that silently defaults {@link CandidateLanguage#displayOrder()} - every caller must state
     * the CV presentation order explicitly, since it is now a real factual invariant.
     */
    @Test
    void constructor_explicitDisplayOrder_isPreserved() {
        CandidateLanguage language = new CandidateLanguage("en", "FLUENT", 2);

        assertThat(language.displayOrder()).isEqualTo(2);
    }

    @Test
    void constructor_negativeDisplayOrder_isRejected() {
        assertThatThrownBy(() -> new CandidateLanguage("en", "FLUENT", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_proficiencyLongerThanDatabaseColumn_isDomainValid() {
        // The database column (candidate_profile_language.proficiency VARCHAR(50)) is the sole
        // length authority - the domain intentionally does not duplicate that constraint. See
        // CandidateProfileRepositoryAdapterTest's public-port rollback test, which relies on this
        // being constructible to prove child-write failure atomicity through the real port.
        CandidateLanguage language = new CandidateLanguage("en", "A".repeat(51), 0);

        assertThat(language.proficiency()).hasSize(51);
    }
}

package com.darya.jobassistant.candidates.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.candidates.PreferenceImportance;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.candidates.aggregate.CandidateLanguage;
import com.darya.jobassistant.candidates.aggregate.CandidatePreferenceType;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;
import com.darya.jobassistant.candidates.aggregate.CandidateProfilePreference;
import com.darya.jobassistant.candidates.aggregate.CandidateSkill;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidateProfileFactsAssemblerTest {

    private final CandidateProfileAggregate aggregate = new CandidateProfileAggregate(
            UUID.randomUUID(), "primary", "Senior Java Backend Engineer", "Senior", 6,
            null, null, null, null, null, null,
            "Poland", true, "8000 EUR/month",
            List.of(new CandidateSkill("Java", "Language", "10+ years", SkillProficiency.EXPERT)),
            List.of(new CandidateLanguage("en", "native", 0), new CandidateLanguage("pl", "conversational", 1)),
            List.of(
                    new CandidateProfilePreference(CandidatePreferenceType.WORK_ARRANGEMENT, "Remote", PreferenceImportance.STRONG),
                    new CandidateProfilePreference(CandidatePreferenceType.ALLOWED_WORK_COUNTRY, "Poland", null),
                    new CandidateProfilePreference(CandidatePreferenceType.CONTRACT_TYPE, "B2B", PreferenceImportance.PREFERRED, 0),
                    new CandidateProfilePreference(CandidatePreferenceType.COMPANY_TYPE, "Product", PreferenceImportance.PREFERRED)),
            3L);

    @Test
    void toProfileFacts_mapsScalarFields() {
        CandidateProfileFacts facts = CandidateProfileFactsAssembler.toProfileFacts(aggregate);

        assertThat(facts.targetRole()).isEqualTo("Senior Java Backend Engineer");
        assertThat(facts.targetSeniority()).isEqualTo("Senior");
        assertThat(facts.experienceYears()).isEqualTo(6);
    }

    @Test
    void toProfileFacts_skillsPreserveNameProficiencyNoteAndCategory() {
        CandidateProfileFacts facts = CandidateProfileFactsAssembler.toProfileFacts(aggregate);

        assertThat(facts.skills()).hasSize(1);
        assertThat(facts.skills().get(0).name()).isEqualTo("Java");
        assertThat(facts.skills().get(0).category()).isEqualTo("Language");
        assertThat(facts.skills().get(0).note()).isEqualTo("10+ years");
        assertThat(facts.skills().get(0).proficiency()).isEqualTo(SkillProficiency.EXPERT);
    }

    /**
     * Sprint 11 Step 2: {@code candidate_profile_skill.id} already exists as a persisted column
     * (inherited from {@code BaseEntity}) but was previously dropped by every mapper on the way to
     * {@code CandidateProfileFacts} - now propagated unchanged so a future CV tailoring result can
     * reference an existing skill by stable identity instead of inventing a free-form skill name.
     */
    @Test
    void toProfileFacts_propagatesPersistedSkillId() {
        UUID skillId = UUID.randomUUID();
        CandidateProfileAggregate aggregateWithSkillId = new CandidateProfileAggregate(
                UUID.randomUUID(), "primary", "Senior Java Backend Engineer", "Senior", 6,
                null, null, null, null, null, null,
                null, false, null,
                List.of(new CandidateSkill(skillId, "Java", "Language", "10+ years", SkillProficiency.EXPERT)),
                List.of(), List.of(), 0L);

        CandidateProfileFacts facts = CandidateProfileFactsAssembler.toProfileFacts(aggregateWithSkillId);

        assertThat(facts.skills()).hasSize(1);
        assertThat(facts.skills().get(0).candidateSkillId()).isEqualTo(skillId);
    }

    @Test
    void toProfileFacts_languagesPreserveResolvedNameAndProficiency() {
        CandidateProfileFacts facts = CandidateProfileFactsAssembler.toProfileFacts(aggregate);

        assertThat(facts.languages()).containsExactlyInAnyOrder(
                new com.darya.jobassistant.candidates.CandidateLanguageFacts("English", "native"),
                new com.darya.jobassistant.candidates.CandidateLanguageFacts("Polish", "conversational"));
    }

    @Test
    void toProfileFacts_languageWithNoProficiencyMetadata_producesNullProficiency() {
        CandidateProfileAggregate withoutLanguageProficiency = new CandidateProfileAggregate(
                UUID.randomUUID(), "primary", "Senior Java Backend Engineer", "Senior", 6,
                null, null, null, null, null, null,
                null, false, null, List.of(), List.of(new CandidateLanguage("en", null, 0)), List.of(), 0L);

        CandidateProfileFacts facts = CandidateProfileFactsAssembler.toProfileFacts(withoutLanguageProficiency);

        assertThat(facts.languages()).containsExactly(new com.darya.jobassistant.candidates.CandidateLanguageFacts("English", null));
    }

    @Test
    void toProfileFacts_weightedPreferencesAreReconstructedCorrectly() {
        CandidatePreferences preferences = CandidateProfileFactsAssembler.toProfileFacts(aggregate).preferences();

        assertThat(preferences.currentCountry()).isEqualTo("Poland");
        assertThat(preferences.preferredWorkArrangement()).isEqualTo("Remote");
        assertThat(preferences.workArrangementImportance()).isEqualTo(PreferenceImportance.STRONG);
        assertThat(preferences.allowedWorkCountries()).containsExactly("Poland");
        assertThat(preferences.relocationAllowed()).isTrue();
        assertThat(preferences.preferredContractTypes()).containsExactly("B2B");
        assertThat(preferences.contractTypeImportance()).isEqualTo(PreferenceImportance.PREFERRED);
        assertThat(preferences.preferredCompanyType()).isEqualTo("Product");
        assertThat(preferences.companyTypeImportance()).isEqualTo(PreferenceImportance.PREFERRED);
        assertThat(preferences.salaryExpectation()).isEqualTo("8000 EUR/month");
    }

    @Test
    void toProfileFacts_nullAggregate_isRejected() {
        assertThatThrownBy(() -> CandidateProfileFactsAssembler.toProfileFacts(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

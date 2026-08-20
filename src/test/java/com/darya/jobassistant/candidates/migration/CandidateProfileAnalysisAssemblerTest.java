package com.darya.jobassistant.candidates.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidates.CandidateLanguageEntry;
import com.darya.jobassistant.candidates.CandidateLanguageFacts;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
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

class CandidateProfileAnalysisAssemblerTest {

    private final CandidateProfileAggregate aggregate = new CandidateProfileAggregate(
            UUID.randomUUID(), "primary", "Senior Java Backend Engineer", "Senior", 6,
            null, null, null, null, null, null,
            "Poland", true, "8000 EUR/month",
            List.of(new CandidateSkill("Java", "Language", "10+ years", SkillProficiency.EXPERT)),
            List.of(new CandidateLanguage("en", null, 0), new CandidateLanguage("pl", null, 1)),
            List.of(
                    new CandidateProfilePreference(CandidatePreferenceType.WORK_ARRANGEMENT, "Remote", PreferenceImportance.STRONG),
                    new CandidateProfilePreference(CandidatePreferenceType.ALLOWED_WORK_COUNTRY, "Poland", null),
                    new CandidateProfilePreference(CandidatePreferenceType.CONTRACT_TYPE, "B2B", PreferenceImportance.PREFERRED, 0),
                    new CandidateProfilePreference(CandidatePreferenceType.COMPANY_TYPE, "Product", PreferenceImportance.PREFERRED)),
            3L);

    @Test
    void toAnalysisProfile_mapsScalarFields() {
        CandidateProfile analysisProfile = CandidateProfileAnalysisAssembler.toAnalysisProfile(aggregate);

        assertThat(analysisProfile.targetRole()).isEqualTo("Senior Java Backend Engineer");
        assertThat(analysisProfile.targetSeniority()).isEqualTo("Senior");
        assertThat(analysisProfile.experienceYears()).isEqualTo(6);
    }

    @Test
    void toAnalysisProfile_skillsPreserveNameProficiencyAndNote() {
        CandidateProfile analysisProfile = CandidateProfileAnalysisAssembler.toAnalysisProfile(aggregate);

        assertThat(analysisProfile.skills()).hasSize(1);
        assertThat(analysisProfile.skills().get(0).name()).isEqualTo("Java");
        assertThat(analysisProfile.skills().get(0).proficiency()).isEqualTo(SkillProficiency.EXPERT);
        assertThat(analysisProfile.skills().get(0).note()).isEqualTo("10+ years");
    }

    @Test
    void toAnalysisProfile_languagesReconstructedAsDisplayNames() {
        CandidateProfile analysisProfile = CandidateProfileAnalysisAssembler.toAnalysisProfile(aggregate);

        assertThat(analysisProfile.languages()).extracting(CandidateLanguageEntry::language)
                .containsExactlyInAnyOrder("English", "Polish");
    }

    @Test
    void toAnalysisProfile_weightedPreferencesAreReconstructedCorrectly() {
        CandidatePreferences preferences = CandidateProfileAnalysisAssembler.toAnalysisProfile(aggregate).preferences();

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
    void toAnalysisProfile_noPersistenceIdOrVersionLeaksIntoResult() {
        // CandidateProfile has no id/version fields at all - this is a structural guarantee, but
        // asserting the assembled result is unaffected by changing only id/version below makes
        // that guarantee explicit and regression-proof.
        CandidateProfileAggregate sameContentDifferentIdentity = new CandidateProfileAggregate(
                UUID.randomUUID(), aggregate.profileKey(), aggregate.targetRole(), aggregate.seniority(),
                aggregate.experienceYears(), aggregate.preferredCompanyType(), aggregate.preferredLocation(),
                aggregate.employmentModel(), aggregate.remotePolicy(), aggregate.salaryCurrency(), aggregate.minimumSalary(),
                aggregate.currentCountry(), aggregate.relocationAllowed(), aggregate.salaryExpectationNote(),
                aggregate.skills(), aggregate.languages(), aggregate.preferences(), 99L);

        CandidateProfile first = CandidateProfileAnalysisAssembler.toAnalysisProfile(aggregate);
        CandidateProfile second = CandidateProfileAnalysisAssembler.toAnalysisProfile(sameContentDifferentIdentity);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void toAnalysisProfile_noPreferencesAtAll_producesEmptyPreferencesShape() {
        CandidateProfileAggregate withoutPreferences = new CandidateProfileAggregate(
                null, "primary", "Backend Engineer", "Senior", 6,
                null, null, null, null, null, null,
                null, false, null,
                List.of(), List.of(), List.of(), 0L);

        CandidatePreferences preferences = CandidateProfileAnalysisAssembler.toAnalysisProfile(withoutPreferences).preferences();

        assertThat(preferences.currentCountry()).isNull();
        assertThat(preferences.preferredWorkArrangement()).isNull();
        assertThat(preferences.allowedWorkCountries()).isEmpty();
        assertThat(preferences.relocationAllowed()).isFalse();
        assertThat(preferences.preferredContractTypes()).isEmpty();
        assertThat(preferences.preferredCompanyType()).isNull();
        assertThat(preferences.salaryExpectation()).isNull();
    }

    @Test
    void toAnalysisProfile_nullAggregate_isRejected() {
        assertThatThrownBy(() -> CandidateProfileAnalysisAssembler.toAnalysisProfile((CandidateProfileAggregate) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toAnalysisProfile_nullFacts_isRejected() {
        assertThatThrownBy(() -> CandidateProfileAnalysisAssembler.toAnalysisProfile((CandidateProfileFacts) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Acceptance correction: skill category is still dropped (unchanged narrowing), but language
     * proficiency is no longer dropped - see this class's javadoc for why the migration parity
     * check now requires that.
     */
    @Test
    void toAnalysisProfile_fromFacts_dropsSkillCategoryButPreservesLanguageProficiency() {
        CandidateProfileFacts facts = new CandidateProfileFacts(
                "Senior Java Backend Engineer", "Senior",
                List.of(new CandidateSkillFacts("Java", "Language", "10+ years", SkillProficiency.EXPERT)),
                List.of(new CandidateLanguageFacts("English", "native")),
                6, new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null));

        CandidateProfile analysisProfile = CandidateProfileAnalysisAssembler.toAnalysisProfile(facts);

        assertThat(analysisProfile.skills().get(0).name()).isEqualTo("Java");
        assertThat(analysisProfile.skills().get(0).proficiency()).isEqualTo(SkillProficiency.EXPERT);
        assertThat(analysisProfile.skills().get(0).note()).isEqualTo("10+ years");
        assertThat(analysisProfile.languages()).containsExactly(new CandidateLanguageEntry("English", "native"));
    }

    @Test
    void toAnalysisProfile_aggregateOverload_isConsistentWithFactsAssemblerComposition() {
        CandidateProfile viaAggregate = CandidateProfileAnalysisAssembler.toAnalysisProfile(aggregate);
        CandidateProfile viaFacts =
                CandidateProfileAnalysisAssembler.toAnalysisProfile(CandidateProfileFactsAssembler.toProfileFacts(aggregate));

        assertThat(viaAggregate).isEqualTo(viaFacts);
    }

    @Test
    void roundTrip_yamlToAggregateToYaml_reproducesTheOriginalSemanticContent() {
        CandidatePreferences preferences = new CandidatePreferences(
                "Poland", "Remote", PreferenceImportance.STRONG, List.of("Poland", "Germany"), true,
                List.of("B2B"), PreferenceImportance.PREFERRED, "Product", PreferenceImportance.PREFERRED, "8000 EUR/month");
        CandidateProfile original = new CandidateProfile(
                "Senior Java Backend Engineer", "Senior",
                List.of(new com.darya.jobassistant.candidates.CandidateSkill("Java", SkillProficiency.EXPERT, null)),
                List.of(new CandidateLanguageEntry("English", "Fluent"), new CandidateLanguageEntry("Polish", "Conversational")),
                6, preferences);

        CandidateProfileAggregate mapped = CandidateProfileYamlImportMapper.toAggregate(original, "primary");
        CandidateProfile reassembled = CandidateProfileAnalysisAssembler.toAnalysisProfile(mapped);

        CandidateProfileAggregate normalizedOriginal = CandidateProfileYamlImportMapper.toAggregate(original, "check");
        CandidateProfileAggregate normalizedReassembled = CandidateProfileYamlImportMapper.toAggregate(reassembled, "check");
        assertThat(CandidateProfileSemanticComparator.areEqual(normalizedOriginal, normalizedReassembled)).isTrue();
    }

    /**
     * Sprint 9 Step 3 correction requirement 1: the documented candidate geo-policy fallback order
     * survives an aggregate round trip unchanged, even though - per {@code
     * CandidatePreferenceType.ALLOWED_WORK_COUNTRY}'s javadoc and {@code
     * JobSearchQueryPlannerTest} - that specific list is not itself order-significant in current
     * production behavior. {@code CONTRACT_TYPE}, the type that actually is order-significant (see
     * {@code CandidatePreferenceType.CONTRACT_TYPE}), is exercised here too.
     */
    @Test
    void roundTrip_orderedContractTypesSurviveExactly_inOriginalListPosition() {
        CandidateProfileAggregate aggregate = new CandidateProfileAggregate(
                UUID.randomUUID(), "primary", "Senior Java Backend Engineer", "Senior", 6,
                null, null, null, null, null, null,
                "Poland", true, null, List.of(), List.of(),
                List.of(
                        new CandidateProfilePreference(CandidatePreferenceType.ALLOWED_WORK_COUNTRY, "Poland", null),
                        new CandidateProfilePreference(CandidatePreferenceType.CONTRACT_TYPE, "B2B", PreferenceImportance.PREFERRED, 0),
                        new CandidateProfilePreference(CandidatePreferenceType.CONTRACT_TYPE, "EOR", PreferenceImportance.PREFERRED, 1),
                        new CandidateProfilePreference(CandidatePreferenceType.CONTRACT_TYPE, "Full-time", PreferenceImportance.PREFERRED, 2)),
                0L);

        CandidatePreferences preferences = CandidateProfileAnalysisAssembler.toAnalysisProfile(aggregate).preferences();

        assertThat(preferences.preferredContractTypes()).containsExactly("B2B", "EOR", "Full-time");
    }

    /**
     * Same proof, but with the underlying preference rows supplied out of {@code priorityOrder}
     * sequence - as they would be if loaded from a datastore with no row-order guarantee. The
     * assembler must not trust incoming list order for an order-significant type; it must sort by
     * {@code priorityOrder} itself.
     */
    @Test
    void roundTrip_orderedContractTypes_reconstructedCorrectly_evenWhenInputRowsAreOutOfOrder() {
        CandidateProfileAggregate aggregate = new CandidateProfileAggregate(
                UUID.randomUUID(), "primary", "Senior Java Backend Engineer", "Senior", 6,
                null, null, null, null, null, null,
                null, false, null, List.of(), List.of(),
                List.of(
                        new CandidateProfilePreference(CandidatePreferenceType.CONTRACT_TYPE, "Full-time", PreferenceImportance.PREFERRED, 2),
                        new CandidateProfilePreference(CandidatePreferenceType.CONTRACT_TYPE, "B2B", PreferenceImportance.PREFERRED, 0),
                        new CandidateProfilePreference(CandidatePreferenceType.CONTRACT_TYPE, "EOR", PreferenceImportance.PREFERRED, 1)),
                0L);

        CandidatePreferences preferences = CandidateProfileAnalysisAssembler.toAnalysisProfile(aggregate).preferences();

        assertThat(preferences.preferredContractTypes()).containsExactly("B2B", "EOR", "Full-time");
    }
}

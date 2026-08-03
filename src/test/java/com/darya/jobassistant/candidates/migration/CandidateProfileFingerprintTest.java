package com.darya.jobassistant.candidates.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.candidates.PreferenceImportance;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.candidates.aggregate.CandidateLanguage;
import com.darya.jobassistant.candidates.aggregate.CandidatePreferenceType;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;
import com.darya.jobassistant.candidates.aggregate.CandidateProfilePreference;
import com.darya.jobassistant.candidates.aggregate.CandidateSkill;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidateProfileFingerprintTest {

    @Test
    void sha256_identicalSemanticProfiles_produceIdenticalFingerprints() {
        CandidateProfileAggregate a = profile(UUID.randomUUID(), 0L,
                List.of(skill("Java"), skill("Kafka")), List.of(lang("en"), lang("pl")));
        CandidateProfileAggregate b = profile(UUID.randomUUID(), 5L,
                List.of(skill("Java"), skill("Kafka")), List.of(lang("en"), lang("pl")));

        assertThat(CandidateProfileFingerprint.sha256(a)).isEqualTo(CandidateProfileFingerprint.sha256(b));
    }

    @Test
    void sha256_differentIdAndVersion_doNotAffectFingerprint() {
        CandidateProfileAggregate a = profile(UUID.randomUUID(), 0L, List.of(skill("Java")), List.of(lang("en")));
        CandidateProfileAggregate b = profile(UUID.randomUUID(), 42L, List.of(skill("Java")), List.of(lang("en")));

        assertThat(CandidateProfileFingerprint.sha256(a)).isEqualTo(CandidateProfileFingerprint.sha256(b));
    }

    @Test
    void sha256_nonSemanticSkillOrderingDifference_producesTheSameFingerprint() {
        CandidateProfileAggregate a = profile(UUID.randomUUID(), 0L, List.of(skill("Java"), skill("Kafka")), List.of());
        CandidateProfileAggregate b = profile(UUID.randomUUID(), 0L, List.of(skill("Kafka"), skill("Java")), List.of());

        assertThat(CandidateProfileFingerprint.sha256(a)).isEqualTo(CandidateProfileFingerprint.sha256(b));
    }

    @Test
    void sha256_meaningfulSkillChange_changesTheFingerprint() {
        CandidateProfileAggregate a = profile(UUID.randomUUID(), 0L, List.of(skill("Java")), List.of());
        CandidateProfileAggregate b = profile(UUID.randomUUID(), 0L, List.of(skill("Kotlin")), List.of());

        assertThat(CandidateProfileFingerprint.sha256(a)).isNotEqualTo(CandidateProfileFingerprint.sha256(b));
    }

    @Test
    void sha256_meaningfulPreferenceChange_changesTheFingerprint() {
        CandidateProfileAggregate a = profileWithPreference("Product");
        CandidateProfileAggregate b = profileWithPreference("Startup");

        assertThat(CandidateProfileFingerprint.sha256(a)).isNotEqualTo(CandidateProfileFingerprint.sha256(b));
    }

    // ---- Preference ordering (Sprint 9 Step 3 correction) ----

    @Test
    void sha256_reversedContractTypeOrder_producesADifferentFingerprint() {
        CandidateProfileAggregate a = profileWithContractTypes(List.of("B2B", "EOR"));
        CandidateProfileAggregate b = profileWithContractTypes(List.of("EOR", "B2B"));

        assertThat(CandidateProfileFingerprint.sha256(a)).isNotEqualTo(CandidateProfileFingerprint.sha256(b));
    }

    @Test
    void sha256_reversedAllowedWorkCountryOrder_producesTheSameFingerprint() {
        CandidateProfileAggregate a = profileWithAllowedWorkCountries(List.of("Poland", "Germany"));
        CandidateProfileAggregate b = profileWithAllowedWorkCountries(List.of("Germany", "Poland"));

        assertThat(CandidateProfileFingerprint.sha256(a)).isEqualTo(CandidateProfileFingerprint.sha256(b));
    }

    private CandidateProfileAggregate profileWithContractTypes(List<String> contractTypesInOrder) {
        List<CandidateProfilePreference> preferences = new java.util.ArrayList<>();
        for (int i = 0; i < contractTypesInOrder.size(); i++) {
            preferences.add(new CandidateProfilePreference(
                    CandidatePreferenceType.CONTRACT_TYPE, contractTypesInOrder.get(i), PreferenceImportance.PREFERRED, i));
        }
        return new CandidateProfileAggregate(
                UUID.randomUUID(), "primary", "Backend Engineer", "Senior", 6,
                null, null, null, null, null, new BigDecimal("5000.00"),
                null, false, null, List.of(), List.of(), preferences, 0L);
    }

    private CandidateProfileAggregate profileWithAllowedWorkCountries(List<String> countries) {
        List<CandidateProfilePreference> preferences = countries.stream()
                .map(country -> new CandidateProfilePreference(CandidatePreferenceType.ALLOWED_WORK_COUNTRY, country, null))
                .toList();
        return new CandidateProfileAggregate(
                UUID.randomUUID(), "primary", "Backend Engineer", "Senior", 6,
                null, null, null, null, null, new BigDecimal("5000.00"),
                null, false, null, List.of(), List.of(), preferences, 0L);
    }

    private CandidateProfileAggregate profileWithPreference(String companyType) {
        return new CandidateProfileAggregate(
                UUID.randomUUID(), "primary", "Backend Engineer", "Senior", 6,
                null, null, null, null, null, new BigDecimal("5000.00"),
                null, false, null, List.of(), List.of(),
                List.of(new CandidateProfilePreference(CandidatePreferenceType.COMPANY_TYPE, companyType, PreferenceImportance.PREFERRED)),
                0L);
    }

    private CandidateProfileAggregate profile(UUID id, long version, List<CandidateSkill> skills, List<CandidateLanguage> languages) {
        return new CandidateProfileAggregate(
                id, "primary", "Backend Engineer", "Senior", 6,
                null, null, null, null, null, new BigDecimal("5000.00"),
                "Poland", false, null, skills, languages, List.of(), version);
    }

    private CandidateSkill skill(String name) {
        return new CandidateSkill(name, null, null, SkillProficiency.STRONG);
    }

    private CandidateLanguage lang(String code) {
        return new CandidateLanguage(code, null);
    }
}

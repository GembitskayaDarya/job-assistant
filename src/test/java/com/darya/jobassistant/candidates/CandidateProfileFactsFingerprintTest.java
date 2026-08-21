package com.darya.jobassistant.candidates;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CandidateProfileFactsFingerprintTest {

    @Test
    void equivalentFacts_haveTheSameFingerprint() {
        CandidateProfileFacts a = profile(List.of(skill("Java")), List.of(language("English")));
        CandidateProfileFacts b = profile(List.of(skill("Java")), List.of(language("English")));

        assertThat(CandidateProfileFactsFingerprint.sha256(a)).isEqualTo(CandidateProfileFactsFingerprint.sha256(b));
    }

    @Test
    void repeatedComputation_isStable() {
        CandidateProfileFacts facts = profile(List.of(skill("Java")), List.of(language("English")));

        assertThat(CandidateProfileFactsFingerprint.sha256(facts)).isEqualTo(CandidateProfileFactsFingerprint.sha256(facts));
    }

    @Test
    void candidateSkillId_doesNotAffectFingerprint() {
        CandidateProfileFacts withoutId = profile(List.of(new CandidateSkillFacts("Java", null, null, SkillProficiency.STRONG)), List.of());
        CandidateProfileFacts withId = profile(
                List.of(new CandidateSkillFacts(java.util.UUID.randomUUID(), "Java", null, null, SkillProficiency.STRONG)), List.of());

        assertThat(CandidateProfileFactsFingerprint.sha256(withoutId)).isEqualTo(CandidateProfileFactsFingerprint.sha256(withId));
    }

    @Test
    void skillAdded_changesFingerprint() {
        CandidateProfileFacts a = profile(List.of(skill("Java")), List.of());
        CandidateProfileFacts b = profile(List.of(skill("Java"), skill("Kotlin")), List.of());

        assertThat(CandidateProfileFactsFingerprint.sha256(a)).isNotEqualTo(CandidateProfileFactsFingerprint.sha256(b));
    }

    @Test
    void skillRemoved_changesFingerprint() {
        CandidateProfileFacts a = profile(List.of(skill("Java"), skill("Kotlin")), List.of());
        CandidateProfileFacts b = profile(List.of(skill("Java")), List.of());

        assertThat(CandidateProfileFactsFingerprint.sha256(a)).isNotEqualTo(CandidateProfileFactsFingerprint.sha256(b));
    }

    @Test
    void skillNameChange_changesFingerprint() {
        CandidateProfileFacts a = profile(List.of(skill("Java")), List.of());
        CandidateProfileFacts b = profile(List.of(skill("Kotlin")), List.of());

        assertThat(CandidateProfileFactsFingerprint.sha256(a)).isNotEqualTo(CandidateProfileFactsFingerprint.sha256(b));
    }

    @Test
    void skillProficiencyChange_changesFingerprint() {
        CandidateProfileFacts a = profile(List.of(new CandidateSkillFacts("Java", null, null, SkillProficiency.STRONG)), List.of());
        CandidateProfileFacts b = profile(List.of(new CandidateSkillFacts("Java", null, null, SkillProficiency.WORKING)), List.of());

        assertThat(CandidateProfileFactsFingerprint.sha256(a)).isNotEqualTo(CandidateProfileFactsFingerprint.sha256(b));
    }

    @Test
    void skillDisplayOrder_isPositional_reorderChangesFingerprint() {
        CandidateProfileFacts a = profile(List.of(skill("Java"), skill("Kotlin")), List.of());
        CandidateProfileFacts b = profile(List.of(skill("Kotlin"), skill("Java")), List.of());

        assertThat(CandidateProfileFactsFingerprint.sha256(a)).isNotEqualTo(CandidateProfileFactsFingerprint.sha256(b));
    }

    @Test
    void languageAdded_changesFingerprint() {
        CandidateProfileFacts a = profile(List.of(), List.of(language("English")));
        CandidateProfileFacts b = profile(List.of(), List.of(language("English"), language("Polish")));

        assertThat(CandidateProfileFactsFingerprint.sha256(a)).isNotEqualTo(CandidateProfileFactsFingerprint.sha256(b));
    }

    @Test
    void languageProficiencyChange_changesFingerprint() {
        CandidateProfileFacts a = profile(List.of(), List.of(new CandidateLanguageFacts("Polish", "Fluent")));
        CandidateProfileFacts b = profile(List.of(), List.of(new CandidateLanguageFacts("Polish", "Native")));

        assertThat(CandidateProfileFactsFingerprint.sha256(a)).isNotEqualTo(CandidateProfileFactsFingerprint.sha256(b));
    }

    @Test
    void languageOrder_isPositional_reorderChangesFingerprint() {
        CandidateProfileFacts a = profile(List.of(), List.of(language("English"), language("Polish")));
        CandidateProfileFacts b = profile(List.of(), List.of(language("Polish"), language("English")));

        assertThat(CandidateProfileFactsFingerprint.sha256(a)).isNotEqualTo(CandidateProfileFactsFingerprint.sha256(b));
    }

    @Test
    void educationAdded_changesFingerprint() {
        CandidateProfileFacts a = profileWithEducation(List.of(education("State University", 0)));
        CandidateProfileFacts b = profileWithEducation(List.of(education("State University", 0), education("Tech Institute", 1)));

        assertThat(CandidateProfileFactsFingerprint.sha256(a)).isNotEqualTo(CandidateProfileFactsFingerprint.sha256(b));
    }

    @Test
    void educationInstitutionChange_changesFingerprint() {
        CandidateProfileFacts a = profileWithEducation(List.of(education("State University", 0)));
        CandidateProfileFacts b = profileWithEducation(List.of(education("Tech Institute", 0)));

        assertThat(CandidateProfileFactsFingerprint.sha256(a)).isNotEqualTo(CandidateProfileFactsFingerprint.sha256(b));
    }

    @Test
    void educationDisplayOrderChange_changesFingerprint() {
        CandidateProfileFacts a = profileWithEducation(List.of(education("State University", 0), education("Tech Institute", 1)));
        CandidateProfileFacts b = profileWithEducation(List.of(education("State University", 1), education("Tech Institute", 0)));

        assertThat(CandidateProfileFactsFingerprint.sha256(a)).isNotEqualTo(CandidateProfileFactsFingerprint.sha256(b));
    }

    @Test
    void educationListOrder_isCanonicalizedByDisplayOrder_notInputOrder() {
        CandidateEducationFacts first = education("State University", 0);
        CandidateEducationFacts second = education("Tech Institute", 1);
        CandidateProfileFacts builtInOrder = profileWithEducation(List.of(first, second));
        CandidateProfileFacts builtReversed = profileWithEducation(List.of(second, first));

        assertThat(CandidateProfileFactsFingerprint.sha256(builtInOrder)).isEqualTo(CandidateProfileFactsFingerprint.sha256(builtReversed));
    }

    @Test
    void candidateEducationId_doesNotAffectFingerprint() {
        CandidateProfileFacts withoutId = profileWithEducation(List.of(education("State University", 0)));
        CandidateProfileFacts withId = profileWithEducation(List.of(new CandidateEducationFacts(
                java.util.UUID.randomUUID(), "State University", null, null, null, null, null, null, 0)));

        assertThat(CandidateProfileFactsFingerprint.sha256(withoutId)).isEqualTo(CandidateProfileFactsFingerprint.sha256(withId));
    }

    @Test
    void headerFactChange_fullName_changesFingerprint() {
        CandidateProfileFacts a = profileWithHeader("Jane Doe");
        CandidateProfileFacts b = profileWithHeader("Jane Smith");

        assertThat(CandidateProfileFactsFingerprint.sha256(a)).isNotEqualTo(CandidateProfileFactsFingerprint.sha256(b));
    }

    @Test
    void targetRoleChange_changesFingerprint() {
        CandidateProfileFacts a = new CandidateProfileFacts("Senior Java Backend Engineer", "Senior", List.of(), List.of(), 5, preferences());
        CandidateProfileFacts b = new CandidateProfileFacts("Staff Java Backend Engineer", "Senior", List.of(), List.of(), 5, preferences());

        assertThat(CandidateProfileFactsFingerprint.sha256(a)).isNotEqualTo(CandidateProfileFactsFingerprint.sha256(b));
    }

    @Test
    void preferencesChange_changesFingerprint() {
        CandidateProfileFacts a = new CandidateProfileFacts("Senior Java Backend Engineer", "Senior", List.of(), List.of(), 5,
                new CandidatePreferences(null, "Remote", null, List.of(), false, List.of(), null, null, null, null));
        CandidateProfileFacts b = new CandidateProfileFacts("Senior Java Backend Engineer", "Senior", List.of(), List.of(), 5,
                new CandidatePreferences(null, "Hybrid", null, List.of(), false, List.of(), null, null, null, null));

        assertThat(CandidateProfileFactsFingerprint.sha256(a)).isNotEqualTo(CandidateProfileFactsFingerprint.sha256(b));
    }

    private CandidateProfileFacts profile(List<CandidateSkillFacts> skills, List<CandidateLanguageFacts> languages) {
        return new CandidateProfileFacts("Senior Java Backend Engineer", "Senior", skills, languages, 5, preferences());
    }

    private CandidateProfileFacts profileWithEducation(List<CandidateEducationFacts> education) {
        return new CandidateProfileFacts("Senior Java Backend Engineer", "Senior", List.of(), List.of(), 5, preferences(),
                null, null, null, null, null, education);
    }

    private CandidateProfileFacts profileWithHeader(String fullName) {
        return new CandidateProfileFacts("Senior Java Backend Engineer", "Senior", List.of(), List.of(), 5, preferences(),
                fullName, "jane@example.com", null, null, null, null, List.of());
    }

    private CandidatePreferences preferences() {
        return new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null);
    }

    private CandidateSkillFacts skill(String name) {
        return new CandidateSkillFacts(name, null, null, SkillProficiency.STRONG);
    }

    private CandidateLanguageFacts language(String name) {
        return new CandidateLanguageFacts(name, "Fluent");
    }

    private CandidateEducationFacts education(String institution, int displayOrder) {
        return new CandidateEducationFacts(null, institution, null, null, null, null, null, null, displayOrder);
    }
}

package com.darya.jobassistant.personalprojects.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersonalProjectFingerprintTest {

    private final UUID candidateProfileId = UUID.randomUUID();

    @Test
    void equivalentProjectLists_haveTheSameFingerprint() {
        List<PersonalProject> a = List.of(project("AI Job Search Assistant", 0));
        List<PersonalProject> b = List.of(project("AI Job Search Assistant", 0));

        assertThat(PersonalProjectFingerprint.sha256(a)).isEqualTo(PersonalProjectFingerprint.sha256(b));
    }

    @Test
    void repeatedComputation_isStable() {
        List<PersonalProject> projects = List.of(project("AI Job Search Assistant", 0));

        assertThat(PersonalProjectFingerprint.sha256(projects)).isEqualTo(PersonalProjectFingerprint.sha256(projects));
    }

    @Test
    void emptyList_hasADeterministicFingerprint() {
        assertThat(PersonalProjectFingerprint.sha256(List.of())).isEqualTo(PersonalProjectFingerprint.sha256(List.of()));
    }

    @Test
    void idAndVersion_doNotAffectFingerprint() {
        PersonalProject withoutIds = new PersonalProject(candidateProfileId, "AI Job Search Assistant", "A hobby project", null,
                null, null, 0, List.of(), List.of());
        PersonalProject withIds = new PersonalProject(UUID.randomUUID(), candidateProfileId, "AI Job Search Assistant",
                "A hobby project", null, null, null, 0, List.of(), List.of(), 7L);

        assertThat(PersonalProjectFingerprint.sha256(List.of(withoutIds))).isEqualTo(PersonalProjectFingerprint.sha256(List.of(withIds)));
    }

    @Test
    void topLevelListOrder_isCanonicalizedByDisplayOrder_notInputOrder() {
        PersonalProject first = project("AI Job Search Assistant", 0);
        PersonalProject second = project("Recipe Tracker", 1);
        List<PersonalProject> builtInOrder = List.of(first, second);
        List<PersonalProject> builtReversed = List.of(second, first);

        assertThat(PersonalProjectFingerprint.sha256(builtInOrder)).isEqualTo(PersonalProjectFingerprint.sha256(builtReversed));
    }

    @Test
    void projectAdded_changesFingerprint() {
        List<PersonalProject> a = List.of(project("AI Job Search Assistant", 0));
        List<PersonalProject> b = List.of(project("AI Job Search Assistant", 0), project("Recipe Tracker", 1));

        assertThat(PersonalProjectFingerprint.sha256(a)).isNotEqualTo(PersonalProjectFingerprint.sha256(b));
    }

    @Test
    void projectRemoved_changesFingerprint() {
        List<PersonalProject> a = List.of(project("AI Job Search Assistant", 0), project("Recipe Tracker", 1));
        List<PersonalProject> b = List.of(project("AI Job Search Assistant", 0));

        assertThat(PersonalProjectFingerprint.sha256(a)).isNotEqualTo(PersonalProjectFingerprint.sha256(b));
    }

    @Test
    void descriptionChange_changesFingerprint() {
        PersonalProject a = new PersonalProject(candidateProfileId, "AI Job Search Assistant", "First description", null,
                null, null, 0, List.of(), List.of());
        PersonalProject b = new PersonalProject(candidateProfileId, "AI Job Search Assistant", "Second description", null,
                null, null, 0, List.of(), List.of());

        assertThat(PersonalProjectFingerprint.sha256(List.of(a))).isNotEqualTo(PersonalProjectFingerprint.sha256(List.of(b)));
    }

    @Test
    void highlightAdded_changesFingerprint() {
        PersonalProject a = new PersonalProject(candidateProfileId, "AI Job Search Assistant", null, null, null, null, 0,
                List.of(new PersonalProjectHighlight("Automated CV generation", 0)), List.of());
        PersonalProject b = new PersonalProject(candidateProfileId, "AI Job Search Assistant", null, null, null, null, 0,
                List.of(new PersonalProjectHighlight("Automated CV generation", 0), new PersonalProjectHighlight("Cut manual effort by 80%", 1)),
                List.of());

        assertThat(PersonalProjectFingerprint.sha256(List.of(a))).isNotEqualTo(PersonalProjectFingerprint.sha256(List.of(b)));
    }

    @Test
    void highlightTextChange_changesFingerprint() {
        PersonalProject a = new PersonalProject(candidateProfileId, "AI Job Search Assistant", null, null, null, null, 0,
                List.of(new PersonalProjectHighlight("Automated CV generation", 0)), List.of());
        PersonalProject b = new PersonalProject(candidateProfileId, "AI Job Search Assistant", null, null, null, null, 0,
                List.of(new PersonalProjectHighlight("Automated cover letter generation", 0)), List.of());

        assertThat(PersonalProjectFingerprint.sha256(List.of(a))).isNotEqualTo(PersonalProjectFingerprint.sha256(List.of(b)));
    }

    @Test
    void technologyAdded_changesFingerprint() {
        PersonalProject a = new PersonalProject(candidateProfileId, "AI Job Search Assistant", null, null, null, null, 0,
                List.of(), List.of(new PersonalProjectTechnology("Java", null, 0)));
        PersonalProject b = new PersonalProject(candidateProfileId, "AI Job Search Assistant", null, null, null, null, 0,
                List.of(), List.of(new PersonalProjectTechnology("Java", null, 0), new PersonalProjectTechnology("Spring Boot", null, 1)));

        assertThat(PersonalProjectFingerprint.sha256(List.of(a))).isNotEqualTo(PersonalProjectFingerprint.sha256(List.of(b)));
    }

    @Test
    void technologyNameChange_changesFingerprint() {
        PersonalProject a = new PersonalProject(candidateProfileId, "AI Job Search Assistant", null, null, null, null, 0,
                List.of(), List.of(new PersonalProjectTechnology("Java", null, 0)));
        PersonalProject b = new PersonalProject(candidateProfileId, "AI Job Search Assistant", null, null, null, null, 0,
                List.of(), List.of(new PersonalProjectTechnology("Kotlin", null, 0)));

        assertThat(PersonalProjectFingerprint.sha256(List.of(a))).isNotEqualTo(PersonalProjectFingerprint.sha256(List.of(b)));
    }

    @Test
    void displayOrderChange_changesFingerprint() {
        List<PersonalProject> a = List.of(project("AI Job Search Assistant", 0), project("Recipe Tracker", 1));
        List<PersonalProject> b = List.of(project("AI Job Search Assistant", 1), project("Recipe Tracker", 0));

        assertThat(PersonalProjectFingerprint.sha256(a)).isNotEqualTo(PersonalProjectFingerprint.sha256(b));
    }

    private PersonalProject project(String name, int displayOrder) {
        return new PersonalProject(candidateProfileId, name, null, null, null, null, displayOrder, List.of(), List.of());
    }
}

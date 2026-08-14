package com.darya.jobassistant.personalprojects.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersonalProjectTest {

    private final UUID candidateProfileId = UUID.randomUUID();

    @Test
    void constructor_validProject_isCreated() {
        PersonalProject project = new PersonalProject(
                candidateProfileId, "Example Project", "A factual description", "https://github.com/example/project",
                LocalDate.of(2022, 1, 1), LocalDate.of(2022, 6, 1), 0,
                List.of(new PersonalProjectHighlight("Built a REST API", 0)),
                List.of(new PersonalProjectTechnology("Java", "Language", 0)));

        assertThat(project.name()).isEqualTo("Example Project");
        assertThat(project.highlights()).hasSize(1);
        assertThat(project.technologies()).hasSize(1);
        assertThat(project.version()).isZero();
        assertThat(project.id()).isNull();
    }

    @Test
    void constructor_nullCandidateProfileId_isRejected() {
        assertThatThrownBy(() -> new PersonalProject(
                null, "Example Project", null, null, null, null, 0, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_blankName_isRejected() {
        assertThatThrownBy(() -> new PersonalProject(
                candidateProfileId, "   ", null, null, null, null, 0, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negativeDisplayOrder_isRejected() {
        assertThatThrownBy(() -> new PersonalProject(
                candidateProfileId, "Example Project", null, null, null, null, -1, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_endDateBeforeStartDate_isRejected() {
        assertThatThrownBy(() -> new PersonalProject(
                candidateProfileId, "Example Project", null, null,
                LocalDate.of(2022, 6, 1), LocalDate.of(2022, 1, 1), 0, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_highlightsSortedByDisplayOrder_regardlessOfInputOrder() {
        PersonalProjectHighlight second = new PersonalProjectHighlight("Second", 1);
        PersonalProjectHighlight first = new PersonalProjectHighlight("First", 0);

        PersonalProject project = new PersonalProject(
                candidateProfileId, "Example Project", null, null, null, null, 0,
                List.of(second, first), List.of());

        assertThat(project.highlights()).extracting(PersonalProjectHighlight::text).containsExactly("First", "Second");
    }

    @Test
    void constructor_duplicateHighlightDisplayOrder_isRejected() {
        List<PersonalProjectHighlight> highlights = List.of(
                new PersonalProjectHighlight("First", 0),
                new PersonalProjectHighlight("Second", 0));

        assertThatThrownBy(() -> new PersonalProject(
                candidateProfileId, "Example Project", null, null, null, null, 0, highlights, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_duplicateTechnologyDisplayOrder_isRejected() {
        List<PersonalProjectTechnology> technologies = List.of(
                new PersonalProjectTechnology("Java", null, 0),
                new PersonalProjectTechnology("Kafka", null, 0));

        assertThatThrownBy(() -> new PersonalProject(
                candidateProfileId, "Example Project", null, null, null, null, 0, List.of(), technologies))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Sprint 11 Step 5 correction: duplicate technology names must be rejected, case-insensitively. */
    @Test
    void constructor_duplicateTechnologyNamesDifferingOnlyByCase_areRejected() {
        List<PersonalProjectTechnology> technologies = List.of(
                new PersonalProjectTechnology("Kafka", null, 0),
                new PersonalProjectTechnology("kafka", null, 1));

        assertThatThrownBy(() -> new PersonalProject(
                candidateProfileId, "Example Project", null, null, null, null, 0, List.of(), technologies))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_duplicateTechnologyNamesDifferingByWhitespace_areRejected() {
        List<PersonalProjectTechnology> technologies = List.of(
                new PersonalProjectTechnology("Kafka", null, 0),
                new PersonalProjectTechnology(" Kafka ", null, 1));

        assertThatThrownBy(() -> new PersonalProject(
                candidateProfileId, "Example Project", null, null, null, null, 0, List.of(), technologies))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_distinctTechnologyNames_areAccepted() {
        List<PersonalProjectTechnology> technologies = List.of(
                new PersonalProjectTechnology("Java", null, 0),
                new PersonalProjectTechnology("Kafka", null, 1));

        PersonalProject project = new PersonalProject(
                candidateProfileId, "Example Project", null, null, null, null, 0, List.of(), technologies);

        assertThat(project.technologies()).hasSize(2);
    }

    /** Sprint 11 Step 5 correction: unlike technology names, duplicate project names across a candidate's projects are allowed - name is not identity. */
    @Test
    void constructor_projectNameIsNotValidatedForUniqueness_onlyTechnologyNamesAre() {
        PersonalProject first = new PersonalProject(candidateProfileId, "Same Name", null, null, null, null, 0, List.of(), List.of());
        PersonalProject second = new PersonalProject(candidateProfileId, "Same Name", null, null, null, null, 1, List.of(), List.of());

        assertThat(first.name()).isEqualTo(second.name());
    }
}

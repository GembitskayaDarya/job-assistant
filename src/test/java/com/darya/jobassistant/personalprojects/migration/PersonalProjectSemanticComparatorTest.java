package com.darya.jobassistant.personalprojects.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.personalprojects.aggregate.PersonalProject;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectHighlight;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectTechnology;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersonalProjectSemanticComparatorTest {

    @Test
    void areEqual_identicalFactualContentWithDifferentIdsAndVersion_isTrue() {
        PersonalProject a = fullProject(UUID.randomUUID(), 0L, "en");
        PersonalProject b = fullProject(UUID.randomUUID(), 9L, "en");

        assertThat(PersonalProjectSemanticComparator.areEqual(a, b)).isTrue();
    }

    @Test
    void areEqual_technologyNameDifferingOnlyByCaseOrWhitespace_isStillEqual() {
        PersonalProject a = fullProject(UUID.randomUUID(), 0L, "Kafka");
        PersonalProject b = fullProject(UUID.randomUUID(), 0L, " kafka ");

        assertThat(PersonalProjectSemanticComparator.areEqual(a, b)).isTrue();
    }

    @Test
    void areEqual_differentName_isFalse() {
        PersonalProject a = project("Example Project");
        PersonalProject b = project("Different Project");

        assertThat(PersonalProjectSemanticComparator.areEqual(a, b)).isFalse();
    }

    @Test
    void areEqual_differentDescription_isFalse() {
        PersonalProject a = withDescription("A description");
        PersonalProject b = withDescription("A different description");

        assertThat(PersonalProjectSemanticComparator.areEqual(a, b)).isFalse();
    }

    @Test
    void areEqual_differentUrl_isFalse() {
        PersonalProject a = withUrl("https://example.com/a");
        PersonalProject b = withUrl("https://example.com/b");

        assertThat(PersonalProjectSemanticComparator.areEqual(a, b)).isFalse();
    }

    @Test
    void areEqual_differentStartDate_isFalse() {
        PersonalProject a = withDates(LocalDate.of(2022, 1, 1), null);
        PersonalProject b = withDates(LocalDate.of(2023, 1, 1), null);

        assertThat(PersonalProjectSemanticComparator.areEqual(a, b)).isFalse();
    }

    @Test
    void areEqual_differentEndDate_isFalse() {
        PersonalProject a = withDates(null, LocalDate.of(2022, 6, 1));
        PersonalProject b = withDates(null, LocalDate.of(2023, 6, 1));

        assertThat(PersonalProjectSemanticComparator.areEqual(a, b)).isFalse();
    }

    @Test
    void areEqual_differentDisplayOrder_isFalse() {
        PersonalProject a = withDisplayOrder(0);
        PersonalProject b = withDisplayOrder(1);

        assertThat(PersonalProjectSemanticComparator.areEqual(a, b)).isFalse();
    }

    @Test
    void areEqual_differentHighlightText_isFalse() {
        PersonalProject a = withHighlights(List.of(new PersonalProjectHighlight("First", 0)));
        PersonalProject b = withHighlights(List.of(new PersonalProjectHighlight("Second", 0)));

        assertThat(PersonalProjectSemanticComparator.areEqual(a, b)).isFalse();
    }

    @Test
    void areEqual_differentHighlightCount_isFalse() {
        PersonalProject a = withHighlights(List.of(new PersonalProjectHighlight("First", 0)));
        PersonalProject b = withHighlights(List.of(new PersonalProjectHighlight("First", 0), new PersonalProjectHighlight("Second", 1)));

        assertThat(PersonalProjectSemanticComparator.areEqual(a, b)).isFalse();
    }

    @Test
    void areEqual_reorderedHighlights_isFalse() {
        PersonalProject a = withHighlights(List.of(new PersonalProjectHighlight("First", 0), new PersonalProjectHighlight("Second", 1)));
        PersonalProject b = withHighlights(List.of(new PersonalProjectHighlight("Second", 0), new PersonalProjectHighlight("First", 1)));

        assertThat(PersonalProjectSemanticComparator.areEqual(a, b)).isFalse();
    }

    @Test
    void areEqual_differentTechnologyCategory_isFalse() {
        PersonalProject a = withTechnologies(List.of(new PersonalProjectTechnology("Java", "Language", 0)));
        PersonalProject b = withTechnologies(List.of(new PersonalProjectTechnology("Java", "Platform", 0)));

        assertThat(PersonalProjectSemanticComparator.areEqual(a, b)).isFalse();
    }

    private PersonalProject fullProject(UUID id, long version, String technologyName) {
        return new PersonalProject(
                id, UUID.randomUUID(), "Example Project", "A description", "https://example.com",
                LocalDate.of(2022, 1, 1), LocalDate.of(2022, 6, 1), 0,
                List.of(new PersonalProjectHighlight("Built a REST API", 0)),
                List.of(new PersonalProjectTechnology(technologyName, "Messaging", 0)), version);
    }

    private PersonalProject project(String name) {
        return new PersonalProject(UUID.randomUUID(), name, null, null, null, null, 0, List.of(), List.of());
    }

    private PersonalProject withDescription(String description) {
        return new PersonalProject(UUID.randomUUID(), "Example Project", description, null, null, null, 0, List.of(), List.of());
    }

    private PersonalProject withUrl(String url) {
        return new PersonalProject(UUID.randomUUID(), "Example Project", null, url, null, null, 0, List.of(), List.of());
    }

    private PersonalProject withDates(LocalDate startDate, LocalDate endDate) {
        return new PersonalProject(UUID.randomUUID(), "Example Project", null, null, startDate, endDate, 0, List.of(), List.of());
    }

    private PersonalProject withDisplayOrder(int displayOrder) {
        return new PersonalProject(UUID.randomUUID(), "Example Project", null, null, null, null, displayOrder, List.of(), List.of());
    }

    private PersonalProject withHighlights(List<PersonalProjectHighlight> highlights) {
        return new PersonalProject(UUID.randomUUID(), "Example Project", null, null, null, null, 0, highlights, List.of());
    }

    private PersonalProject withTechnologies(List<PersonalProjectTechnology> technologies) {
        return new PersonalProject(UUID.randomUUID(), "Example Project", null, null, null, null, 0, List.of(), technologies);
    }
}

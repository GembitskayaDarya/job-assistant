package com.darya.jobassistant.vacancyextraction.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExtractedVacancyDataTest {

    @Test
    void legacySevenArgConstructor_defaultsSalaryAmountsCurrencyAndPostedDateToNull() {
        ExtractedVacancyData data = new ExtractedVacancyData(
                "Title", "Company", "Europe", RemotePolicy.REMOTE, List.of("B2B"), List.of("Java"), "10-15k PLN");

        assertThat(data.salaryMin()).isNull();
        assertThat(data.salaryMax()).isNull();
        assertThat(data.currency()).isNull();
        assertThat(data.salaryText()).isEqualTo("10-15k PLN");
        assertThat(data.postedDate()).isNull();
    }

    @Test
    void canonicalConstructor_populatesSalaryAmountsCurrencyAndPostedDate() {
        ExtractedVacancyData data = new ExtractedVacancyData(
                "Title", "Company", "Europe", RemotePolicy.REMOTE, List.of("B2B"), List.of("Java"),
                new BigDecimal("10000"), new BigDecimal("15000"), "  PLN  ", "10-15k PLN", LocalDate.of(2026, 1, 15));

        assertThat(data.salaryMin()).isEqualByComparingTo("10000");
        assertThat(data.salaryMax()).isEqualByComparingTo("15000");
        assertThat(data.currency()).isEqualTo("PLN");
        assertThat(data.postedDate()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Test
    void constructor_blankCurrency_normalizesToNull() {
        ExtractedVacancyData data = new ExtractedVacancyData(
                "Title", "Company", null, RemotePolicy.UNSPECIFIED, List.of(), List.of(), null, null, "   ", null, null);

        assertThat(data.currency()).isNull();
    }

    @Test
    void constructor_validRequiredFields_areAcceptedAndTrimmed() {
        ExtractedVacancyData data = new ExtractedVacancyData(
                "  Senior Java Backend Developer  ",
                "  Example Company  ",
                "Europe",
                RemotePolicy.REMOTE,
                List.of("B2B"),
                List.of("Java", "Spring Boot"),
                "10-15k PLN");

        assertThat(data.title()).isEqualTo("Senior Java Backend Developer");
        assertThat(data.company()).isEqualTo("Example Company");
        assertThat(data.location()).isEqualTo("Europe");
        assertThat(data.remotePolicy()).isEqualTo(RemotePolicy.REMOTE);
        assertThat(data.contractTypes()).containsExactly("B2B");
        assertThat(data.requiredSkills()).containsExactly("Java", "Spring Boot");
        assertThat(data.salaryText()).isEqualTo("10-15k PLN");
    }

    @Test
    void constructor_blankOptionalStrings_becomeNull() {
        ExtractedVacancyData data = new ExtractedVacancyData(
                "Title", "Company", "   ", RemotePolicy.UNSPECIFIED, List.of(), List.of(), "  ");

        assertThat(data.location()).isNull();
        assertThat(data.salaryText()).isNull();
    }

    @Test
    void constructor_nullRemotePolicy_defaultsToUnspecified() {
        ExtractedVacancyData data = new ExtractedVacancyData("Title", "Company", null, null, List.of(), List.of(), null);

        assertThat(data.remotePolicy()).isEqualTo(RemotePolicy.UNSPECIFIED);
    }

    @Test
    void constructor_nullCollections_becomeEmptyImmutableLists() {
        ExtractedVacancyData data =
                new ExtractedVacancyData("Title", "Company", null, RemotePolicy.UNSPECIFIED, null, null, null);

        assertThat(data.contractTypes()).isEmpty();
        assertThat(data.requiredSkills()).isEmpty();
        assertThatThrownBy(() -> data.contractTypes().add("B2B")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> data.requiredSkills().add("Java")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constructor_duplicateSkillsAndContractTypes_areRemovedPreservingOrder() {
        ExtractedVacancyData data = new ExtractedVacancyData(
                "Title",
                "Company",
                null,
                RemotePolicy.UNSPECIFIED,
                Arrays.asList("B2B", "UoP", "B2B", " B2B "),
                Arrays.asList("Java", "Kafka", "java", "Java"),
                null);

        assertThat(data.contractTypes()).containsExactly("B2B", "UoP");
        // Case-sensitive de-duplication: "java" and "Java" are kept as distinct entries because
        // only exact-match duplicates (after trimming) are collapsed.
        assertThat(data.requiredSkills()).containsExactly("Java", "Kafka", "java");
    }

    @Test
    void constructor_blankListEntries_areDropped() {
        ExtractedVacancyData data = new ExtractedVacancyData(
                "Title", "Company", null, RemotePolicy.UNSPECIFIED, Arrays.asList("B2B", "  ", null), List.of(), null);

        assertThat(data.contractTypes()).containsExactly("B2B");
    }

    @Test
    void equalsAndHashCode_recordSemantics_areImmutableValueEquality() {
        ExtractedVacancyData first = new ExtractedVacancyData(
                "Title", "Company", "Europe", RemotePolicy.REMOTE, List.of("B2B"), List.of("Java"), "10k");
        ExtractedVacancyData second = new ExtractedVacancyData(
                "Title", "Company", "Europe", RemotePolicy.REMOTE, List.of("B2B"), List.of("Java"), "10k");

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}

package com.darya.jobassistant.vacancyextraction.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.vacancyextraction.exception.VacancyExtractionException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExtractedVacancyDataValidatorTest {

    @Test
    void validate_validSalaryRangeAndCurrency_isAccepted() {
        ExtractedVacancyData data = new ExtractedVacancyData(
                "Title", "Company", null, RemotePolicy.UNSPECIFIED, List.of(), List.of(),
                new BigDecimal("10000"), new BigDecimal("15000"), "PLN", "10000-15000 PLN", null);

        assertThat(ExtractedVacancyDataValidator.validate(data)).isSameAs(data);
    }

    @Test
    void validate_zeroSalaryMin_isRejected() {
        ExtractedVacancyData data = new ExtractedVacancyData(
                "Title", "Company", null, RemotePolicy.UNSPECIFIED, List.of(), List.of(),
                BigDecimal.ZERO, new BigDecimal("15000"), null, "0-15000", null);

        assertThatThrownBy(() -> ExtractedVacancyDataValidator.validate(data))
                .isInstanceOf(VacancyExtractionException.class);
    }

    @Test
    void validate_negativeSalaryMax_isRejected() {
        ExtractedVacancyData data = new ExtractedVacancyData(
                "Title", "Company", null, RemotePolicy.UNSPECIFIED, List.of(), List.of(),
                null, new BigDecimal("-100"), null, "negative salary", null);

        assertThatThrownBy(() -> ExtractedVacancyDataValidator.validate(data))
                .isInstanceOf(VacancyExtractionException.class);
    }

    @Test
    void validate_salaryMinGreaterThanSalaryMax_isRejected() {
        ExtractedVacancyData data = new ExtractedVacancyData(
                "Title", "Company", null, RemotePolicy.UNSPECIFIED, List.of(), List.of(),
                new BigDecimal("20000"), new BigDecimal("10000"), null, "20000-10000", null);

        assertThatThrownBy(() -> ExtractedVacancyDataValidator.validate(data))
                .isInstanceOf(VacancyExtractionException.class);
    }

    @Test
    void validate_salaryMinEqualToSalaryMax_isAccepted() {
        ExtractedVacancyData data = new ExtractedVacancyData(
                "Title", "Company", null, RemotePolicy.UNSPECIFIED, List.of(), List.of(),
                new BigDecimal("10000"), new BigDecimal("10000"), null, "10000 flat", null);

        assertThat(ExtractedVacancyDataValidator.validate(data)).isSameAs(data);
    }

    @Test
    void validate_currencyWithoutAnySalaryInformation_isRejected() {
        ExtractedVacancyData data = new ExtractedVacancyData(
                "Title", "Company", null, RemotePolicy.UNSPECIFIED, List.of(), List.of(),
                null, null, "USD", null, null);

        assertThatThrownBy(() -> ExtractedVacancyDataValidator.validate(data))
                .isInstanceOf(VacancyExtractionException.class);
    }

    @Test
    void validate_currencyWithOnlySalaryText_isAccepted() {
        ExtractedVacancyData data = new ExtractedVacancyData(
                "Title", "Company", null, RemotePolicy.UNSPECIFIED, List.of(), List.of(),
                null, null, "USD", "Competitive, paid in USD", null);

        assertThat(ExtractedVacancyDataValidator.validate(data)).isSameAs(data);
    }

    @Test
    void validate_currencyExceedingMaxLength_isRejected() {
        ExtractedVacancyData data = new ExtractedVacancyData(
                "Title", "Company", null, RemotePolicy.UNSPECIFIED, List.of(), List.of(),
                new BigDecimal("100"), null, "C".repeat(11), "100 C's", null);

        assertThatThrownBy(() -> ExtractedVacancyDataValidator.validate(data))
                .isInstanceOf(VacancyExtractionException.class);
    }

    @Test
    void validate_noSalaryInformationAndNoCurrency_isAccepted() {
        ExtractedVacancyData data = new ExtractedVacancyData(
                "Title", "Company", null, RemotePolicy.UNSPECIFIED, List.of(), List.of(),
                null, null, null, null, null);

        assertThat(ExtractedVacancyDataValidator.validate(data)).isSameAs(data);
    }

    @Test
    void validate_nullData_isRejected() {
        assertThatThrownBy(() -> ExtractedVacancyDataValidator.validate(null))
                .isInstanceOf(VacancyExtractionException.class);
    }

    @Test
    void validate_validRequiredFields_areAcceptedAndReturnedUnchanged() {
        ExtractedVacancyData data = new ExtractedVacancyData(
                "Senior Java Backend Developer",
                "Example Company",
                "Europe",
                RemotePolicy.REMOTE,
                List.of("B2B"),
                List.of("Java", "Kafka"),
                "10-15k PLN");

        ExtractedVacancyData result = ExtractedVacancyDataValidator.validate(data);

        assertThat(result).isSameAs(data);
    }

    @Test
    void validate_blankTitle_isRejected() {
        ExtractedVacancyData data =
                new ExtractedVacancyData("   ", "Example Company", null, RemotePolicy.UNSPECIFIED, List.of(), List.of(), null);

        assertThatThrownBy(() -> ExtractedVacancyDataValidator.validate(data))
                .isInstanceOf(VacancyExtractionException.class);
    }

    @Test
    void validate_nullTitle_isRejected() {
        ExtractedVacancyData data =
                new ExtractedVacancyData(null, "Example Company", null, RemotePolicy.UNSPECIFIED, List.of(), List.of(), null);

        assertThatThrownBy(() -> ExtractedVacancyDataValidator.validate(data))
                .isInstanceOf(VacancyExtractionException.class);
    }

    @Test
    void validate_blankCompany_isRejected() {
        ExtractedVacancyData data = new ExtractedVacancyData(
                "Senior Java Backend Developer", "   ", null, RemotePolicy.UNSPECIFIED, List.of(), List.of(), null);

        assertThatThrownBy(() -> ExtractedVacancyDataValidator.validate(data))
                .isInstanceOf(VacancyExtractionException.class);
    }

    @Test
    void validate_titleExceedingMaxLength_isRejected() {
        ExtractedVacancyData data = new ExtractedVacancyData(
                "T".repeat(301), "Example Company", null, RemotePolicy.UNSPECIFIED, List.of(), List.of(), null);

        assertThatThrownBy(() -> ExtractedVacancyDataValidator.validate(data))
                .isInstanceOf(VacancyExtractionException.class);
    }

    @Test
    void validate_companyExceedingMaxLength_isRejected() {
        ExtractedVacancyData data = new ExtractedVacancyData(
                "Senior Java Backend Developer", "C".repeat(301), null, RemotePolicy.UNSPECIFIED, List.of(), List.of(), null);

        assertThatThrownBy(() -> ExtractedVacancyDataValidator.validate(data))
                .isInstanceOf(VacancyExtractionException.class);
    }

    @Test
    void validate_locationExceedingMaxLength_isRejected() {
        ExtractedVacancyData data = new ExtractedVacancyData(
                "Title", "Company", "L".repeat(301), RemotePolicy.UNSPECIFIED, List.of(), List.of(), null);

        assertThatThrownBy(() -> ExtractedVacancyDataValidator.validate(data))
                .isInstanceOf(VacancyExtractionException.class);
    }

    @Test
    void validate_salaryTextExceedingMaxLength_isRejected() {
        ExtractedVacancyData data = new ExtractedVacancyData(
                "Title", "Company", null, RemotePolicy.UNSPECIFIED, List.of(), List.of(), "S".repeat(201));

        assertThatThrownBy(() -> ExtractedVacancyDataValidator.validate(data))
                .isInstanceOf(VacancyExtractionException.class);
    }

    @Test
    void validate_tooManyRequiredSkills_isRejected() {
        List<String> tooManySkills = new ArrayList<>();
        for (int i = 0; i < 31; i++) {
            tooManySkills.add("Skill" + i);
        }
        ExtractedVacancyData data =
                new ExtractedVacancyData("Title", "Company", null, RemotePolicy.UNSPECIFIED, List.of(), tooManySkills, null);

        assertThatThrownBy(() -> ExtractedVacancyDataValidator.validate(data))
                .isInstanceOf(VacancyExtractionException.class);
    }

    @Test
    void validate_tooManyContractTypes_isRejected() {
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < 31; i++) {
            tooMany.add("Type" + i);
        }
        ExtractedVacancyData data =
                new ExtractedVacancyData("Title", "Company", null, RemotePolicy.UNSPECIFIED, tooMany, List.of(), null);

        assertThatThrownBy(() -> ExtractedVacancyDataValidator.validate(data))
                .isInstanceOf(VacancyExtractionException.class);
    }

    @Test
    void validate_skillEntryExceedingMaxLength_isRejected() {
        ExtractedVacancyData data = new ExtractedVacancyData(
                "Title", "Company", null, RemotePolicy.UNSPECIFIED, List.of(), List.of("S".repeat(101)), null);

        assertThatThrownBy(() -> ExtractedVacancyDataValidator.validate(data))
                .isInstanceOf(VacancyExtractionException.class);
    }

    @Test
    void validate_maximalButWithinBounds_isAccepted() {
        ExtractedVacancyData data = new ExtractedVacancyData(
                "T".repeat(300), "C".repeat(300), "L".repeat(300), RemotePolicy.UNSPECIFIED, List.of(), List.of(), "S".repeat(200));

        assertThat(ExtractedVacancyDataValidator.validate(data)).isSameAs(data);
    }

    @Test
    void validate_returnedData_isTheSameImmutableInstance() {
        ExtractedVacancyData data = new ExtractedVacancyData(
                "Title", "Company", null, RemotePolicy.UNSPECIFIED, Collections.emptyList(), Collections.emptyList(), null);

        ExtractedVacancyData result = ExtractedVacancyDataValidator.validate(data);

        assertThatThrownBy(() -> result.contractTypes().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }
}

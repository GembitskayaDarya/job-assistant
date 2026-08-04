package com.darya.jobassistant.careerhistory.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.darya.jobassistant.careerhistory.importing.source.CareerAchievementImportEntry;
import com.darya.jobassistant.careerhistory.importing.source.CareerCompanyImportEntry;
import com.darya.jobassistant.careerhistory.importing.source.CareerHistoryImportDocument;
import com.darya.jobassistant.careerhistory.importing.source.CareerPositionImportEntry;
import com.darya.jobassistant.careerhistory.importing.source.CareerProjectImportEntry;
import com.darya.jobassistant.careerhistory.importing.source.CareerResponsibilityImportEntry;
import com.darya.jobassistant.careerhistory.importing.source.CareerTechnologyImportEntry;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CareerHistoryImportValidatorTest {

    @Test
    void validDocument_passesWithoutThrowing() {
        assertThatCode(() -> CareerHistoryImportValidator.validate(validDocument())).doesNotThrowAnyException();
    }

    @Test
    void nullSchemaVersion_isRejected() {
        CareerHistoryImportDocument document = withSchemaVersion(validDocument(), null);
        assertViolationPath(document, "schemaVersion");
    }

    @Test
    void unsupportedSchemaVersion_isRejected() {
        CareerHistoryImportDocument document = withSchemaVersion(validDocument(), 2);
        assertViolationPath(document, "schemaVersion");
    }

    @Test
    void blankCandidateProfileKey_isRejected() {
        CareerHistoryImportDocument valid = validDocument();
        CareerHistoryImportDocument document = new CareerHistoryImportDocument(
                valid.schemaVersion(), "  ", valid.expectedVersion(), valid.companies());
        assertViolationPath(document, "candidateProfileKey");
    }

    @Test
    void negativeExpectedVersion_isRejected() {
        CareerHistoryImportDocument valid = validDocument();
        CareerHistoryImportDocument document = new CareerHistoryImportDocument(
                valid.schemaVersion(), valid.candidateProfileKey(), -1L, valid.companies());
        assertViolationPath(document, "expectedVersion");
    }

    @Test
    void nullCompanies_isRejected() {
        CareerHistoryImportDocument valid = validDocument();
        CareerHistoryImportDocument document = new CareerHistoryImportDocument(
                valid.schemaVersion(), valid.candidateProfileKey(), valid.expectedVersion(), null);
        assertViolationPath(document, "companies");
    }

    @Test
    void emptyCompanies_isAllowed() {
        CareerHistoryImportDocument valid = validDocument();
        CareerHistoryImportDocument document = new CareerHistoryImportDocument(
                valid.schemaVersion(), valid.candidateProfileKey(), valid.expectedVersion(), List.of());
        assertThatCode(() -> CareerHistoryImportValidator.validate(document)).doesNotThrowAnyException();
    }

    @Test
    void companyKey_invalidFormat_isRejected() {
        CareerHistoryImportDocument document = withCompanyKey(validDocument(), "Example-Systems");
        assertViolationPath(document, "companies[0].key");
    }

    @Test
    void companyKey_tooLong_isRejected() {
        CareerHistoryImportDocument document = withCompanyKey(validDocument(), "a".repeat(101));
        assertViolationPath(document, "companies[0].key");
    }

    @Test
    void duplicateCompanyKey_isRejected() {
        CareerCompanyImportEntry company = validCompany("example-systems");
        CareerHistoryImportDocument document = new CareerHistoryImportDocument(1, "primary", null, List.of(company, company));

        assertThatThrownBy(() -> CareerHistoryImportValidator.validate(document))
                .isInstanceOf(CareerHistoryImportValidationException.class)
                .satisfies(e -> assertThat(((CareerHistoryImportValidationException) e).violations())
                        .anySatisfy(v -> assertThat(v.violationType()).isEqualTo("DUPLICATE_KEY")));
    }

    @Test
    void duplicateCompanyName_isRejected() {
        CareerCompanyImportEntry first = validCompanyBuilder("example-systems", "Example Systems", 0);
        CareerCompanyImportEntry second = validCompanyBuilder("example-systems-2", "Example Systems", 1);
        CareerHistoryImportDocument document = new CareerHistoryImportDocument(1, "primary", null, List.of(first, second));

        assertThatThrownBy(() -> CareerHistoryImportValidator.validate(document))
                .isInstanceOf(CareerHistoryImportValidationException.class)
                .satisfies(e -> assertThat(((CareerHistoryImportValidationException) e).violations())
                        .anySatisfy(v -> assertThat(v.violationType()).isEqualTo("DUPLICATE_NAME")));
    }

    @Test
    void duplicateCompanyDisplayOrder_isRejected() {
        CareerCompanyImportEntry first = validCompanyBuilder("example-systems", "Example Systems", 0);
        CareerCompanyImportEntry second = validCompanyBuilder("zenith-robotics", "Zenith Robotics", 0);
        CareerHistoryImportDocument document = new CareerHistoryImportDocument(1, "primary", null, List.of(first, second));

        assertThatThrownBy(() -> CareerHistoryImportValidator.validate(document))
                .isInstanceOf(CareerHistoryImportValidationException.class)
                .satisfies(e -> assertThat(((CareerHistoryImportValidationException) e).violations())
                        .anySatisfy(v -> assertThat(v.violationType()).isEqualTo("DUPLICATE_ORDER")));
    }

    @Test
    void duplicatePositionKeyWithinCompany_isRejected() {
        CareerPositionImportEntry position = validPosition("backend", 0);
        CareerCompanyImportEntry company = new CareerCompanyImportEntry(
                "example-systems", "Example Systems", null, null, null, null, 0, List.of(position, withDisplayOrder(position, 1)));
        CareerHistoryImportDocument document = new CareerHistoryImportDocument(1, "primary", null, List.of(company));

        assertThatThrownBy(() -> CareerHistoryImportValidator.validate(document))
                .isInstanceOf(CareerHistoryImportValidationException.class)
                .satisfies(e -> assertThat(((CareerHistoryImportValidationException) e).violations())
                        .anySatisfy(v -> assertThat(v.path()).isEqualTo("companies[0].positions[1].key")
                                .satisfies(p -> assertThat(v.violationType()).isEqualTo("DUPLICATE_KEY"))));
    }

    @Test
    void duplicateProjectKeyWithinPosition_isRejected() {
        CareerProjectImportEntry project = validProject("billing", 0);
        CareerPositionImportEntry position = new CareerPositionImportEntry(
                "backend", "Demo Backend Engineer", null, null, null, LocalDate.of(2021, 1, 1), null, true, null, 0,
                List.of(), List.of(), List.of(project, withProjectDisplayOrder(project, 1)));
        CareerCompanyImportEntry company = new CareerCompanyImportEntry(
                "example-systems", "Example Systems", null, null, null, null, 0, List.of(position));
        CareerHistoryImportDocument document = new CareerHistoryImportDocument(1, "primary", null, List.of(company));

        assertThatThrownBy(() -> CareerHistoryImportValidator.validate(document))
                .isInstanceOf(CareerHistoryImportValidationException.class)
                .satisfies(e -> assertThat(((CareerHistoryImportValidationException) e).violations())
                        .anySatisfy(v -> assertThat(v.violationType()).isEqualTo("DUPLICATE_KEY")));
    }

    @Test
    void companyNameTooLong_isRejected_andValueNotEchoed() {
        String tooLong = "x".repeat(CareerHistoryImportConstraints.COMPANY_NAME_MAX_LENGTH + 1);
        CareerCompanyImportEntry company = new CareerCompanyImportEntry(
                "example-systems", tooLong, null, null, null, null, 0, List.of());
        CareerHistoryImportDocument document = new CareerHistoryImportDocument(1, "primary", null, List.of(company));

        CareerHistoryImportValidationException exception = catchValidationException(document);

        assertThat(exception.violations()).anySatisfy(v -> {
            assertThat(v.path()).isEqualTo("companies[0].name");
            assertThat(v.violationType()).isEqualTo("TOO_LONG");
        });
        assertThat(exception.getMessage()).doesNotContain(tooLong);
    }

    @Test
    void positionTitleTooLong_isRejected() {
        String tooLong = "x".repeat(CareerHistoryImportConstraints.POSITION_TITLE_MAX_LENGTH + 1);
        CareerPositionImportEntry position = new CareerPositionImportEntry(
                "backend", tooLong, null, null, null, LocalDate.of(2021, 1, 1), null, true, null, 0, List.of(), List.of(), List.of());
        CareerCompanyImportEntry company = new CareerCompanyImportEntry(
                "example-systems", "Example Systems", null, null, null, null, 0, List.of(position));
        CareerHistoryImportDocument document = new CareerHistoryImportDocument(1, "primary", null, List.of(company));

        assertViolationPath(document, "companies[0].positions[0].title");
    }

    @Test
    void responsibilityTextTooLong_isRejected() {
        String tooLong = "x".repeat(CareerHistoryImportConstraints.RESPONSIBILITY_TEXT_MAX_LENGTH + 1);
        CareerResponsibilityImportEntry responsibility = new CareerResponsibilityImportEntry(tooLong, 0);
        CareerPositionImportEntry position = new CareerPositionImportEntry(
                "backend", "Demo Backend Engineer", null, null, null, LocalDate.of(2021, 1, 1), null, true, null, 0,
                List.of(responsibility), List.of(), List.of());
        CareerCompanyImportEntry company = new CareerCompanyImportEntry(
                "example-systems", "Example Systems", null, null, null, null, 0, List.of(position));
        CareerHistoryImportDocument document = new CareerHistoryImportDocument(1, "primary", null, List.of(company));

        CareerHistoryImportValidationException exception = catchValidationException(document);
        assertThat(exception.violations()).anySatisfy(v -> assertThat(v.path()).isEqualTo("companies[0].positions[0].responsibilities[0].text"));
        assertThat(exception.getMessage()).doesNotContain(tooLong);
    }

    @Test
    void technologyNameTooLong_isRejected() {
        String tooLong = "x".repeat(CareerHistoryImportConstraints.TECHNOLOGY_NAME_MAX_LENGTH + 1);
        CareerTechnologyImportEntry technology = new CareerTechnologyImportEntry(tooLong, null, 0);
        CareerProjectImportEntry project = new CareerProjectImportEntry(
                "billing", "Billing Platform", null, null, null, 0, List.of(), List.of(), List.of(technology));
        CareerPositionImportEntry position = new CareerPositionImportEntry(
                "backend", "Demo Backend Engineer", null, null, null, LocalDate.of(2021, 1, 1), null, true, null, 0,
                List.of(), List.of(), List.of(project));
        CareerCompanyImportEntry company = new CareerCompanyImportEntry(
                "example-systems", "Example Systems", null, null, null, null, 0, List.of(position));
        CareerHistoryImportDocument document = new CareerHistoryImportDocument(1, "primary", null, List.of(company));

        assertViolationPath(document, "companies[0].positions[0].projects[0].technologies[0].name");
    }

    @Test
    void duplicateTechnologyNameWithinProject_isRejected() {
        CareerTechnologyImportEntry technology = new CareerTechnologyImportEntry("PostgreSQL", null, 0);
        CareerTechnologyImportEntry duplicate = new CareerTechnologyImportEntry("PostgreSQL", null, 1);
        CareerProjectImportEntry project = new CareerProjectImportEntry(
                "billing", "Billing Platform", null, null, null, 0, List.of(), List.of(), List.of(technology, duplicate));
        CareerPositionImportEntry position = new CareerPositionImportEntry(
                "backend", "Demo Backend Engineer", null, null, null, LocalDate.of(2021, 1, 1), null, true, null, 0,
                List.of(), List.of(), List.of(project));
        CareerCompanyImportEntry company = new CareerCompanyImportEntry(
                "example-systems", "Example Systems", null, null, null, null, 0, List.of(position));
        CareerHistoryImportDocument document = new CareerHistoryImportDocument(1, "primary", null, List.of(company));

        CareerHistoryImportValidationException exception = catchValidationException(document);
        assertThat(exception.violations()).anySatisfy(v -> assertThat(v.violationType()).isEqualTo("DUPLICATE_NAME"));
    }

    @Test
    void positionEndDateBeforeStartDate_isRejected() {
        CareerPositionImportEntry position = new CareerPositionImportEntry(
                "backend", "Demo Backend Engineer", null, null, null,
                LocalDate.of(2021, 1, 1), LocalDate.of(2020, 1, 1), false, null, 0, List.of(), List.of(), List.of());
        CareerCompanyImportEntry company = new CareerCompanyImportEntry(
                "example-systems", "Example Systems", null, null, null, null, 0, List.of(position));
        CareerHistoryImportDocument document = new CareerHistoryImportDocument(1, "primary", null, List.of(company));

        assertViolationPath(document, "companies[0].positions[0].endDate");
    }

    @Test
    void currentRoleWithEndDate_isRejected() {
        CareerPositionImportEntry position = new CareerPositionImportEntry(
                "backend", "Demo Backend Engineer", null, null, null,
                LocalDate.of(2021, 1, 1), LocalDate.of(2022, 1, 1), true, null, 0, List.of(), List.of(), List.of());
        CareerCompanyImportEntry company = new CareerCompanyImportEntry(
                "example-systems", "Example Systems", null, null, null, null, 0, List.of(position));
        CareerHistoryImportDocument document = new CareerHistoryImportDocument(1, "primary", null, List.of(company));

        assertViolationPath(document, "companies[0].positions[0].currentRole");
    }

    @Test
    void projectStartsBeforePosition_isRejected() {
        CareerProjectImportEntry project = new CareerProjectImportEntry(
                "billing", "Billing Platform", null, LocalDate.of(2020, 1, 1), null, 0, List.of(), List.of(), List.of());
        CareerPositionImportEntry position = new CareerPositionImportEntry(
                "backend", "Demo Backend Engineer", null, null, null, LocalDate.of(2021, 1, 1), null, true, null, 0,
                List.of(), List.of(), List.of(project));
        CareerCompanyImportEntry company = new CareerCompanyImportEntry(
                "example-systems", "Example Systems", null, null, null, null, 0, List.of(position));
        CareerHistoryImportDocument document = new CareerHistoryImportDocument(1, "primary", null, List.of(company));

        assertViolationPath(document, "companies[0].positions[0].projects[0].startDate");
    }

    @Test
    void missingDisplayOrder_isRejected() {
        CareerCompanyImportEntry company = new CareerCompanyImportEntry(
                "example-systems", "Example Systems", null, null, null, null, null, List.of());
        CareerHistoryImportDocument document = new CareerHistoryImportDocument(1, "primary", null, List.of(company));

        assertViolationPath(document, "companies[0].displayOrder");
    }

    @Test
    void negativeDisplayOrder_isRejected() {
        CareerCompanyImportEntry company = new CareerCompanyImportEntry(
                "example-systems", "Example Systems", null, null, null, null, -1, List.of());
        CareerHistoryImportDocument document = new CareerHistoryImportDocument(1, "primary", null, List.of(company));

        assertViolationPath(document, "companies[0].displayOrder");
    }

    @Test
    void multipleIndependentViolations_areAllCollected_notJustTheFirst() {
        CareerCompanyImportEntry company = new CareerCompanyImportEntry(
                "Invalid Key!", "", null, null, null, null, -1, List.of());
        CareerHistoryImportDocument document = new CareerHistoryImportDocument(null, " ", -5L, List.of(company));

        CareerHistoryImportValidationException exception = catchValidationException(document);

        assertThat(exception.violations().size()).isGreaterThanOrEqualTo(5);
    }

    private CareerHistoryImportValidationException catchValidationException(CareerHistoryImportDocument document) {
        try {
            CareerHistoryImportValidator.validate(document);
        } catch (CareerHistoryImportValidationException e) {
            return e;
        }
        throw new AssertionError("Expected CareerHistoryImportValidationException but validation passed");
    }

    private void assertViolationPath(CareerHistoryImportDocument document, String expectedPath) {
        CareerHistoryImportValidationException exception = catchValidationException(document);
        assertThat(exception.violations()).anySatisfy(v -> assertThat(v.path()).isEqualTo(expectedPath));
    }

    private CareerHistoryImportDocument validDocument() {
        return new CareerHistoryImportDocument(1, "primary", null, List.of(validCompany("example-systems")));
    }

    private CareerHistoryImportDocument withSchemaVersion(CareerHistoryImportDocument document, Integer schemaVersion) {
        return new CareerHistoryImportDocument(schemaVersion, document.candidateProfileKey(), document.expectedVersion(), document.companies());
    }

    private CareerHistoryImportDocument withCompanyKey(CareerHistoryImportDocument document, String key) {
        CareerCompanyImportEntry original = document.companies().get(0);
        CareerCompanyImportEntry withKey = new CareerCompanyImportEntry(
                key, original.name(), original.website(), original.industry(), original.location(),
                original.description(), original.displayOrder(), original.positions());
        return new CareerHistoryImportDocument(document.schemaVersion(), document.candidateProfileKey(), document.expectedVersion(), List.of(withKey));
    }

    private CareerCompanyImportEntry validCompany(String key) {
        return validCompanyBuilder(key, "Example Systems", 0);
    }

    private CareerCompanyImportEntry validCompanyBuilder(String key, String name, int displayOrder) {
        return new CareerCompanyImportEntry(key, name, null, null, null, null, displayOrder, List.of(validPosition("backend", 0)));
    }

    private CareerPositionImportEntry validPosition(String key, int displayOrder) {
        return new CareerPositionImportEntry(key, "Demo Backend Engineer", null, null, null,
                LocalDate.of(2021, 1, 1), null, true, null, displayOrder, List.of(), List.of(), List.of(validProject("billing", 0)));
    }

    private CareerProjectImportEntry validProject(String key, int displayOrder) {
        return new CareerProjectImportEntry(key, "Billing Platform", null, null, null, displayOrder, List.of(), List.of(), List.of());
    }

    private CareerPositionImportEntry withDisplayOrder(CareerPositionImportEntry position, int displayOrder) {
        return new CareerPositionImportEntry(position.key(), position.title(), position.employmentType(), position.location(),
                position.workArrangement(), position.startDate(), position.endDate(), position.currentRole(), position.description(),
                displayOrder, position.responsibilities(), position.achievements(), position.projects());
    }

    private CareerProjectImportEntry withProjectDisplayOrder(CareerProjectImportEntry project, int displayOrder) {
        return new CareerProjectImportEntry(project.key(), project.name(), project.description(), project.startDate(),
                project.endDate(), displayOrder, project.responsibilities(), project.achievements(), project.technologies());
    }
}

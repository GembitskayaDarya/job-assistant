package com.darya.jobassistant.careerhistory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportSourceException;
import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportValidationException;
import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportValidator;
import com.darya.jobassistant.careerhistory.importing.source.CareerHistoryImportDocument;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * Sprint 9 Step 7 source parsing tests - {@link YamlCareerHistoryImportSource} used standalone
 * (no Spring context, matching the class's own framework-light construction) against classpath
 * fixtures under {@code src/test/resources/careerhistory/}.
 */
class YamlCareerHistoryImportSourceTest {

    private final DefaultResourceLoader resourceLoader = new DefaultResourceLoader();

    @Test
    void validFictionalYaml_loadsSuccessfully() {
        CareerHistoryImportDocument document = source("classpath:careerhistory/valid-career-history.yml").load();

        assertThat(document.schemaVersion()).isEqualTo(1);
        assertThat(document.candidateProfileKey()).isEqualTo("primary");
        assertThat(document.companies()).hasSize(1);
        assertThat(document.companies().get(0).name()).isEqualTo("Example Systems");
        assertThat(document.companies().get(0).positions().get(0).projects().get(0).technologies()).hasSize(2);
    }

    @Test
    void missingFile_failsClearly() {
        assertThatThrownBy(() -> source("classpath:careerhistory/does-not-exist.yml").load())
                .isInstanceOf(CareerHistoryImportSourceException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void oversizedFile_fails() {
        YamlCareerHistoryImportSource source = new YamlCareerHistoryImportSource(
                new CareerHistoryImportProperties(CareerHistoryImportRunnerMode.DRY_RUN,
                        "classpath:careerhistory/valid-career-history.yml", 10L),
                resourceLoader);

        assertThatThrownBy(source::load)
                .isInstanceOf(CareerHistoryImportSourceException.class)
                .hasMessageContaining("exceeds the configured maximum size");
    }

    @Test
    void malformedYaml_fails() {
        assertThatThrownBy(() -> source("classpath:careerhistory/malformed.yml").load())
                .isInstanceOf(CareerHistoryImportSourceException.class);
    }

    @Test
    void unknownFields_failParsing() {
        assertThatThrownBy(() -> source("classpath:careerhistory/unknown-field.yml").load())
                .isInstanceOf(CareerHistoryImportSourceException.class);
    }

    @Test
    void duplicateYamlKeys_failParsing() {
        assertThatThrownBy(() -> source("classpath:careerhistory/duplicate-key.yml").load())
                .isInstanceOf(CareerHistoryImportSourceException.class);
    }

    @Test
    void unsupportedSchemaVersion_loadsButFailsValidation() {
        CareerHistoryImportDocument document = source("classpath:careerhistory/unsupported-schema-version.yml").load();

        assertThatThrownBy(() -> CareerHistoryImportValidator.validate(document))
                .isInstanceOf(CareerHistoryImportValidationException.class)
                .satisfies(e -> assertThat(((CareerHistoryImportValidationException) e).violations())
                        .anySatisfy(v -> assertThat(v.path()).isEqualTo("schemaVersion")));
    }

    @Test
    void nullCompanies_loadsButFailsValidation() {
        CareerHistoryImportDocument document = source("classpath:careerhistory/null-companies.yml").load();

        assertThat(document.companies()).isNull();
        assertThatThrownBy(() -> CareerHistoryImportValidator.validate(document))
                .isInstanceOf(CareerHistoryImportValidationException.class)
                .satisfies(e -> assertThat(((CareerHistoryImportValidationException) e).violations())
                        .anySatisfy(v -> assertThat(v.path()).isEqualTo("companies")));
    }

    @Test
    void blankSource_failsAtConstruction() {
        assertThatThrownBy(() -> new YamlCareerHistoryImportSource(
                new CareerHistoryImportProperties(CareerHistoryImportRunnerMode.DRY_RUN, "  ", 5_242_880L), resourceLoader))
                .isInstanceOf(CareerHistoryImportSourceException.class);
    }

    @Test
    void nonPositiveMaxFileSize_failsAtConstruction() {
        assertThatThrownBy(() -> new YamlCareerHistoryImportSource(
                new CareerHistoryImportProperties(
                        CareerHistoryImportRunnerMode.DRY_RUN, "classpath:careerhistory/valid-career-history.yml", 0L),
                resourceLoader))
                .isInstanceOf(CareerHistoryImportSourceException.class);
    }

    private YamlCareerHistoryImportSource source(String location) {
        return new YamlCareerHistoryImportSource(
                new CareerHistoryImportProperties(CareerHistoryImportRunnerMode.DRY_RUN, location, 5_242_880L), resourceLoader);
    }
}

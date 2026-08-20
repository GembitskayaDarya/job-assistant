package com.darya.jobassistant.applicationmaterials.render.ats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.applicationmaterials.render.model.CvDateRangeFormatter;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvCompany;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvDocument;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvHeader;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvPersonalProject;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvPosition;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvProject;
import com.darya.jobassistant.candidates.CandidateEducationFacts;
import com.darya.jobassistant.candidates.CandidateLanguageFacts;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Sprint 11 Big Block 7 (Part 15): all fixtures here use clearly generic/fake data ("Acme Corp",
 * "Test Company") - proving {@link AtsCvVerifier} is genuinely generic (see its own class javadoc):
 * no company/position/skill name is ever hardcoded in the verifier itself, only walked from
 * whatever {@link TailoredCvDocument} it is given.
 */
class AtsCvVerifierTest {

    // ==================== Happy path ====================

    @Test
    void verify_validDocumentWithMatchingExtractedText_isReadable() {
        TailoredCvDocument document = fullDocument();
        String text = renderedTextFor(document);

        AtsVerificationResult result = AtsCvVerifier.verify(document, text);

        assertThat(result.readable()).isTrue();
        assertThat(result.status()).isEqualTo(AtsVerificationStatus.ATS_READABLE);
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void verify_minimalDocumentWithOnlyHeader_isReadable() {
        TailoredCvDocument document = new TailoredCvDocument(
                new TailoredCvHeader("Test Person", "Test Headline", null, null, null, null),
                null, List.of(), List.of(), List.of(), List.of(), List.of());

        AtsVerificationResult result = AtsCvVerifier.verify(document, "Test Person\nTest Headline\n");

        assertThat(result.readable()).isTrue();
    }

    // ==================== No selectable text ====================

    @Test
    void verify_blankExtractedText_failsWithNoSelectableText() {
        AtsVerificationResult result = AtsCvVerifier.verify(fullDocument(), "   ");

        assertThat(result.readable()).isFalse();
        assertThat(result.violations()).extracting(AtsVerificationViolation::category)
                .containsExactly(AtsViolationCategory.NO_SELECTABLE_TEXT);
    }

    @Test
    void verify_nullExtractedText_failsWithNoSelectableText() {
        AtsVerificationResult result = AtsCvVerifier.verify(fullDocument(), null);

        assertThat(result.readable()).isFalse();
        assertThat(result.violations()).extracting(AtsVerificationViolation::category)
                .containsExactly(AtsViolationCategory.NO_SELECTABLE_TEXT);
    }

    // ==================== Missing header content ====================

    @Test
    void verify_missingFullNameInText_fails() {
        TailoredCvDocument document = fullDocument();
        String text = renderedTextFor(document).replace("Test Person", "");

        AtsVerificationResult result = AtsCvVerifier.verify(document, text);

        assertThat(result.violations()).extracting(AtsVerificationViolation::category).contains(AtsViolationCategory.MISSING_FULL_NAME);
    }

    @Test
    void verify_nullHeaderFields_areNullTolerant_neverReportedAsMissing() {
        TailoredCvDocument document = new TailoredCvDocument(
                new TailoredCvHeader(null, null, null, null, null, null), null, List.of(), List.of(), List.of(), List.of(), List.of());

        AtsVerificationResult result = AtsCvVerifier.verify(document, "some placeholder text");

        assertThat(result.readable()).isTrue();
    }

    // ==================== Missing section heading ====================

    @Test
    void verify_missingRequiredSectionHeading_fails() {
        TailoredCvDocument document = fullDocument();
        String text = renderedTextFor(document).replace("Professional Experience", "");

        AtsVerificationResult result = AtsCvVerifier.verify(document, text);

        assertThat(result.violations()).extracting(AtsVerificationViolation::category).contains(AtsViolationCategory.MISSING_SECTION_HEADING);
    }

    @Test
    void verify_emptySection_neverRequiresItsHeading() {
        // No skills, no education, no languages, no personal projects at all - none of their
        // headings should be required.
        TailoredCvPosition position = new TailoredCvPosition("Engineer", null, null, null,
                LocalDate.of(2020, 1, 1), null, true, null, List.of("Did work"), List.of(), List.of());
        TailoredCvCompany company = new TailoredCvCompany("Test Company", null, null, null, null, List.of(position));
        TailoredCvDocument document = new TailoredCvDocument(
                new TailoredCvHeader("Test Person", "Engineer", null, null, null, null),
                null, List.of(), List.of(company), List.of(), List.of(), List.of());
        String text = "Test Person\nEngineer\nProfessional Experience\nTest Company\nEngineer\n"
                + CvDateRangeFormatter.format(LocalDate.of(2020, 1, 1), null, true) + "\nDid work\n";

        AtsVerificationResult result = AtsCvVerifier.verify(document, text);

        assertThat(result.readable()).isTrue();
    }

    // ==================== Experience-before-education ordering ====================

    @Test
    void verify_educationBeforeExperience_fails() {
        TailoredCvDocument document = fullDocument();
        // Swap the two headings' relative order in the extracted text.
        String scrambled = "Test Person\nTest Headline\nEducation\nProfessional Experience\nAcme Corp\nEngineer | 2020\nDid work\n";

        AtsVerificationResult result = AtsCvVerifier.verify(document, scrambled);

        assertThat(result.violations()).extracting(AtsVerificationViolation::category)
                .contains(AtsViolationCategory.EXPERIENCE_NOT_BEFORE_EDUCATION);
    }

    // ==================== Ordering corruption within experience ====================

    @Test
    void verify_positionTitleAppearingBeforeItsOwningCompany_failsOrderingViolation() {
        TailoredCvPosition position = new TailoredCvPosition("Engineer", null, null, null,
                LocalDate.of(2020, 1, 1), null, true, null, List.of("Did work"), List.of(), List.of());
        TailoredCvCompany company = new TailoredCvCompany("Test Company", null, null, null, null, List.of(position));
        TailoredCvDocument document = new TailoredCvDocument(
                new TailoredCvHeader("Test Person", "Engineer", null, null, null, null),
                null, List.of(), List.of(company), List.of(), List.of(), List.of());
        // Position title extracted BEFORE the company name it belongs to - out of reading order.
        String scrambled = "Test Person\nEngineer\nProfessional Experience\nEngineer | 2020\nTest Company\nDid work\n";

        AtsVerificationResult result = AtsCvVerifier.verify(document, scrambled);

        assertThat(result.violations()).extracting(AtsVerificationViolation::category).contains(AtsViolationCategory.ORDERING_VIOLATION);
    }

    @Test
    void verify_achievementTextAppearingBeforeItsPositionTitle_failsOrderingViolation() {
        TailoredCvPosition position = new TailoredCvPosition("Engineer", null, null, null,
                LocalDate.of(2020, 1, 1), null, true, null, List.of(), List.of("Improved reliability"), List.of());
        TailoredCvCompany company = new TailoredCvCompany("Test Company", null, null, null, null, List.of(position));
        TailoredCvDocument document = new TailoredCvDocument(
                new TailoredCvHeader("Test Person", "Engineer", null, null, null, null),
                null, List.of(), List.of(company), List.of(), List.of(), List.of());
        // "Improved reliability" extracted before "Engineer" (the position title) - genuinely
        // out of order relative to the position it belongs to.
        String scrambled = "Test Person\nEngineer\nProfessional Experience\nTest Company\nImproved reliability\nEngineer | 2020\n";

        AtsVerificationResult result = AtsCvVerifier.verify(document, scrambled);

        assertThat(result.readable()).isFalse();
    }

    // ==================== Missing skill term ====================

    @Test
    void verify_missingSkillTerm_fails() {
        TailoredCvDocument document = fullDocument();
        String text = renderedTextFor(document).replace("Java", "");

        AtsVerificationResult result = AtsCvVerifier.verify(document, text);

        assertThat(result.violations()).extracting(AtsVerificationViolation::category).contains(AtsViolationCategory.MISSING_SKILL_TERM);
        assertThat(result.violations()).extracting(AtsVerificationViolation::detail).contains("Java");
    }

    // ==================== Missing personal project / education / language content ====================

    @Test
    void verify_missingPersonalProjectContent_fails() {
        TailoredCvDocument document = fullDocument();
        String text = renderedTextFor(document).replace("Side Project", "");

        AtsVerificationResult result = AtsCvVerifier.verify(document, text);

        assertThat(result.violations()).extracting(AtsVerificationViolation::category).contains(AtsViolationCategory.MISSING_PERSONAL_PROJECT_CONTENT);
    }

    @Test
    void verify_missingEducationContent_fails() {
        TailoredCvDocument document = fullDocument();
        String text = renderedTextFor(document).replace("Test University", "");

        AtsVerificationResult result = AtsCvVerifier.verify(document, text);

        assertThat(result.violations()).extracting(AtsVerificationViolation::category).contains(AtsViolationCategory.MISSING_EDUCATION_CONTENT);
    }

    @Test
    void verify_missingLanguageContent_fails() {
        TailoredCvDocument document = fullDocument();
        String text = renderedTextFor(document).replace("English", "");

        AtsVerificationResult result = AtsCvVerifier.verify(document, text);

        assertThat(result.violations()).extracting(AtsVerificationViolation::category).contains(AtsViolationCategory.MISSING_LANGUAGE_CONTENT);
    }

    // ==================== Accumulation - multiple problems all reported ====================

    @Test
    void verify_multipleIndependentProblems_areAllAccumulated() {
        AtsVerificationResult result = AtsCvVerifier.verify(fullDocument(), "   ");

        assertThat(result.violations()).hasSize(1); // blank text short-circuits to just NO_SELECTABLE_TEXT
    }

    @Test
    void verify_nullDocument_isRejected() {
        assertThatThrownBy(() -> AtsCvVerifier.verify(null, "text")).isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== Fixtures (deliberately generic/fake, never a real candidate's data) ====================

    private TailoredCvDocument fullDocument() {
        TailoredCvProject project = new TailoredCvProject("Test Project", null, LocalDate.of(2021, 1, 1), LocalDate.of(2022, 1, 1),
                List.of(), List.of(), List.of("Java"));
        TailoredCvPosition position = new TailoredCvPosition("Engineer", null, null, null,
                LocalDate.of(2020, 1, 1), null, true, null, List.of("Did work"), List.of(), List.of(project));
        TailoredCvCompany company = new TailoredCvCompany("Acme Corp", null, null, null, null, List.of(position));

        TailoredCvPersonalProject personalProject = new TailoredCvPersonalProject(
                "Side Project", null, null, LocalDate.of(2022, 1, 1), null, List.of("Built a thing"), List.of("Java"));

        return new TailoredCvDocument(
                new TailoredCvHeader("Test Person", "Test Headline", null, "test@example.test", "+1 555 0100", "https://example.test/in/test"),
                "A concise summary.", List.of("Java"), List.of(company), List.of(personalProject),
                List.of(new CandidateEducationFacts(null, "Test University", null, null, null, null, null, null, 0)),
                List.of(new CandidateLanguageFacts("English", "Native")));
    }

    /** Builds extracted text in exactly the order {@link AtsCvVerifier} expects to walk it, so tests can corrupt one specific piece. */
    private String renderedTextFor(TailoredCvDocument document) {
        StringBuilder text = new StringBuilder();
        text.append(document.header().fullName()).append('\n');
        text.append(document.header().cvHeadline()).append('\n');
        text.append(document.header().email()).append('\n');
        text.append(document.header().phone()).append('\n');
        text.append("example.test/in/test\n");
        text.append("Professional Summary\n").append(document.professionalSummary()).append('\n');
        text.append("Technical Skills\n").append(String.join(", ", document.skills())).append('\n');
        text.append("Professional Experience\n");
        for (TailoredCvCompany company : document.experience()) {
            text.append(company.name()).append('\n');
            for (TailoredCvPosition position : company.positions()) {
                text.append(position.title()).append('\n');
                text.append(CvDateRangeFormatter.format(position.startDate(), position.endDate(), position.currentRole())).append('\n');
                position.responsibilities().forEach(r -> text.append(r).append('\n'));
                for (TailoredCvProject project : position.projects()) {
                    text.append(project.name()).append('\n');
                    project.technologies().forEach(t -> text.append(t).append('\n'));
                }
            }
        }
        text.append("Personal Projects\n");
        for (TailoredCvPersonalProject project : document.personalProjects()) {
            text.append(project.name()).append('\n');
        }
        text.append("Education\n");
        for (CandidateEducationFacts education : document.education()) {
            text.append(education.institution()).append('\n');
        }
        text.append("Languages\n");
        for (CandidateLanguageFacts language : document.languages()) {
            text.append(language.name()).append('\n');
        }
        return text.toString();
    }
}

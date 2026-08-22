package com.darya.jobassistant.applicationmaterials.render.ats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.applicationmaterials.render.model.CvDateRangeFormatter;
import com.darya.jobassistant.applicationmaterials.render.model.CvSectionHeadings;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvCompany;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvDocument;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvHeader;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvMentoring;
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

        AtsVerificationResult result = AtsCvVerifier.verify(document, "TEST PERSON\nTest Headline\n");

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
        // Renderer draws the full name uppercase (see AtsCvVerifier#toUppercaseHeading) - renderedTextFor mirrors that.
        String text = renderedTextFor(document).replace("TEST PERSON", "");

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
        String text = renderedTextFor(document).replace(CvSectionHeadings.PROFESSIONAL_EXPERIENCE, "");

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
        String text = "TEST PERSON\nEngineer\n" + CvSectionHeadings.PROFESSIONAL_EXPERIENCE + "\nTEST COMPANY\nEngineer\n"
                + CvDateRangeFormatter.format(LocalDate.of(2020, 1, 1), null, true) + "\nDid work\n";

        AtsVerificationResult result = AtsCvVerifier.verify(document, text);

        assertThat(result.readable()).isTrue();
    }

    // ==================== Experience-before-education ordering ====================

    @Test
    void verify_educationBeforeExperience_fails() {
        TailoredCvDocument document = fullDocument();
        // Swap the two headings' relative order in the extracted text.
        String scrambled = "TEST PERSON\nTest Headline\n" + CvSectionHeadings.EDUCATION + "\n" + CvSectionHeadings.PROFESSIONAL_EXPERIENCE
                + "\nACME CORP\nEngineer | 2020\nDid work\n";

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
        String scrambled = "TEST PERSON\nEngineer\n" + CvSectionHeadings.PROFESSIONAL_EXPERIENCE + "\nEngineer | 2020\nTEST COMPANY\nDid work\n";

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
        String scrambled = "TEST PERSON\nEngineer\n" + CvSectionHeadings.PROFESSIONAL_EXPERIENCE + "\nTEST COMPANY\nImproved reliability\nEngineer | 2020\n";

        AtsVerificationResult result = AtsCvVerifier.verify(document, scrambled);

        assertThat(result.readable()).isFalse();
    }

    // ==================== Long bullet text wrapped onto multiple PDF lines is still found ====================

    /**
     * Production regression: a long Mentoring achievement sentence wraps onto more than one PDF
     * line - the renderer draws a plain space at the wrap point, but the PDF text extractor inserts
     * a newline there instead, so an exact-substring search for the bullet's original (space-only)
     * text used to fail with a false {@code MISSING_MENTORING_CONTENT} violation on every real
     * generation with a long enough bullet. {@link AtsCvVerifier#verify} now whitespace-normalizes
     * both sides before searching (see its javadoc) - proven here directly against extracted text
     * containing a newline exactly where the original sentence had a space.
     */
    @Test
    void verify_longMentoringBulletWrappedAcrossPdfLines_isStillFound() {
        String longBullet = "Contributed to hiring 4 new employees by mentoring students through final projects and technical preparation.";
        TailoredCvMentoring mentoring = new TailoredCvMentoring(
                "Test Education Center", "Mentor", LocalDate.of(2021, 9, 1), LocalDate.of(2021, 12, 31), false, List.of(longBullet));
        TailoredCvDocument document = new TailoredCvDocument(
                new TailoredCvHeader("Test Person", "Engineer", null, null, null, null),
                null, List.of(), List.of(), mentoring, List.of(), List.of(), List.of());

        // Simulates PDFBox's text stripper: the wrap point (a space in the real bullet) becomes a
        // newline in the extracted text - the rest of the sentence is otherwise character-identical.
        String wrappedBullet = "Contributed to hiring 4 new employees by mentoring students through final projects and\ntechnical preparation.";
        String text = "TEST PERSON\nEngineer\n" + CvSectionHeadings.MENTORING_EXPERIENCE
                + "\nTEST EDUCATION CENTER\nMentor | " + CvDateRangeFormatter.format(LocalDate.of(2021, 9, 1), LocalDate.of(2021, 12, 31), false)
                + "\n" + wrappedBullet + "\n";

        AtsVerificationResult result = AtsCvVerifier.verify(document, text);

        assertThat(result.violations()).as("violations: %s", result.violations()).isEmpty();
        assertThat(result.readable()).isTrue();
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

    /**
     * Builds extracted text in exactly the order {@link AtsCvVerifier} expects to walk it, so tests
     * can corrupt one specific piece. Mirrors {@code PdfBoxApplicationMaterialDocumentRenderer}'s
     * uppercase treatment of the full name and each company name (see {@code AtsCvVerifier#
     * toUppercaseHeading}'s javadoc) and {@link CvSectionHeadings}' exact heading text - a synthetic
     * fixture that drifts from either would exercise the wrong renderer/verifier contract.
     */
    private String renderedTextFor(TailoredCvDocument document) {
        StringBuilder text = new StringBuilder();
        text.append(document.header().fullName().toUpperCase(java.util.Locale.ROOT)).append('\n');
        text.append(document.header().cvHeadline()).append('\n');
        text.append(document.header().phone()).append('\n');
        text.append(document.header().email()).append('\n');
        text.append("example.test/in/test\n");
        text.append(CvSectionHeadings.PROFESSIONAL_SUMMARY).append('\n').append(document.professionalSummary()).append('\n');
        text.append(CvSectionHeadings.TECHNICAL_SKILLS).append('\n').append(String.join(", ", document.skills())).append('\n');
        text.append(CvSectionHeadings.PROFESSIONAL_EXPERIENCE).append('\n');
        for (TailoredCvCompany company : document.experience()) {
            text.append(company.name().toUpperCase(java.util.Locale.ROOT)).append('\n');
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
        text.append(CvSectionHeadings.PERSONAL_PROJECT).append('\n');
        for (TailoredCvPersonalProject project : document.personalProjects()) {
            text.append(project.name()).append('\n');
        }
        text.append(CvSectionHeadings.EDUCATION).append('\n');
        for (CandidateEducationFacts education : document.education()) {
            text.append(education.institution()).append('\n');
        }
        text.append(CvSectionHeadings.LANGUAGES).append('\n');
        for (CandidateLanguageFacts language : document.languages()) {
            text.append(language.name()).append('\n');
        }
        return text.toString();
    }
}

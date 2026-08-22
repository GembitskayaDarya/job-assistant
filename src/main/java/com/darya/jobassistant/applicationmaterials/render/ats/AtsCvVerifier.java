package com.darya.jobassistant.applicationmaterials.render.ats;

import com.darya.jobassistant.applicationmaterials.render.model.CvDateRangeFormatter;
import com.darya.jobassistant.applicationmaterials.render.model.CvPhoneDisplay;
import com.darya.jobassistant.applicationmaterials.render.model.CvSectionHeadings;
import com.darya.jobassistant.applicationmaterials.render.model.CvUrlDisplay;
import com.darya.jobassistant.candidates.CandidateEducationFacts;
import com.darya.jobassistant.candidates.CandidateLanguageFacts;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvCompany;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvDocument;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvHeader;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvPersonalProject;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvPosition;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvProject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sprint 11 Big Block 7: deterministic, framework-free structural ATS-readability verification of
 * PDF-extracted text against the exact {@link TailoredCvDocument} it was rendered from - not a
 * predictive score for any particular ATS vendor, only mechanical parseability/structure. Mirrors
 * {@code CvTailoringValidator}'s shape: a stateless, private-constructor utility class, deterministic
 * for a given input, no repository/AI/network/current-time dependency.
 *
 * <h2>Generic by design</h2>
 *
 * Every check below is driven entirely by walking {@code document} itself - no company/position/
 * skill name is ever hardcoded here. A candidate-specific expectation (e.g. "SPRIBE appears before
 * its current role") is a fact about a specific rendered {@code TailoredCvDocument} fixture, which
 * belongs in a test/baseline-config-specific test, never in this class.
 *
 * <h2>Reading-order verification</h2>
 *
 * A single monotonically-advancing text cursor walks the document in its expected reading order
 * (header, summary, skills, experience - company, then each of its positions, then each position's
 * projects, in document order - Personal Projects, Education, Languages); every check searches for
 * its expected text no earlier than the cursor's current position and, on success, advances the
 * cursor past the match. A failed check never advances the cursor, so one out-of-order or missing
 * item does not spuriously cascade into failing every later, correctly-placed item. {@link
 * #verifyExperienceBeforeEducation} is a separate, dedicated check (raw indices, not tied to the
 * shared cursor) so that specific ordering requirement always reports its own precise category.
 *
 * <h2>Optional sections</h2>
 *
 * A section heading (Professional Summary/Technical Skills/Professional Experience/Personal
 * Projects/Education/Languages) is only required to extract when {@code document} actually has
 * content for it - an empty section is never drawn by the renderer, so requiring its heading
 * unconditionally would be a false failure, not a real one.
 */
public final class AtsCvVerifier {

    private AtsCvVerifier() {
    }

    public static AtsVerificationResult verify(TailoredCvDocument document, String extractedText) {
        if (document == null) {
            throw new IllegalArgumentException("ATS verifier document must not be null");
        }
        List<AtsVerificationViolation> violations = new ArrayList<>();
        String text = normalizeWhitespace(extractedText == null ? "" : extractedText);

        if (text.isBlank()) {
            violations.add(new AtsVerificationViolation(AtsViolationCategory.NO_SELECTABLE_TEXT, "extracted PDF text was blank"));
            return new AtsVerificationResult(violations);
        }

        verifyExperienceBeforeEducation(document, text, violations);

        int cursor = 0;
        cursor = verifyHeader(document.header(), text, cursor, violations);

        if (document.professionalSummary() != null && !document.professionalSummary().isBlank()) {
            cursor = requireAfter(text, CvSectionHeadings.PROFESSIONAL_SUMMARY, cursor, AtsViolationCategory.MISSING_SECTION_HEADING, violations);
        }

        if (!document.skills().isEmpty()) {
            cursor = requireAfter(text, CvSectionHeadings.TECHNICAL_SKILLS, cursor, AtsViolationCategory.MISSING_SECTION_HEADING, violations);
            for (String skill : document.skills()) {
                requireSkillTerm(text, skill, violations);
            }
        }

        if (!document.experience().isEmpty()) {
            cursor = requireAfter(text, CvSectionHeadings.PROFESSIONAL_EXPERIENCE, cursor, AtsViolationCategory.MISSING_SECTION_HEADING, violations);
            for (TailoredCvCompany company : document.experience()) {
                cursor = verifyCompany(company, text, cursor, violations);
            }
        }

        if (document.mentoring() != null) {
            cursor = requireAfter(text, CvSectionHeadings.MENTORING_EXPERIENCE, cursor, AtsViolationCategory.MISSING_SECTION_HEADING, violations);
            cursor = requireAfter(text, toUppercaseHeading(document.mentoring().organization()), cursor, AtsViolationCategory.MISSING_MENTORING_CONTENT, violations);
            cursor = requireAfter(text, document.mentoring().title(), cursor, AtsViolationCategory.MISSING_MENTORING_CONTENT, violations);
            for (String bullet : document.mentoring().bullets()) {
                cursor = requireAfter(text, bullet, cursor, AtsViolationCategory.MISSING_MENTORING_CONTENT, violations);
            }
        }

        if (!document.personalProjects().isEmpty()) {
            cursor = requireAfter(text, CvSectionHeadings.PERSONAL_PROJECT, cursor, AtsViolationCategory.MISSING_SECTION_HEADING, violations);
            for (TailoredCvPersonalProject project : document.personalProjects()) {
                cursor = requireAfter(text, project.name(), cursor, AtsViolationCategory.MISSING_PERSONAL_PROJECT_CONTENT, violations);
            }
        }

        if (!document.education().isEmpty()) {
            cursor = requireAfter(text, CvSectionHeadings.EDUCATION, cursor, AtsViolationCategory.MISSING_SECTION_HEADING, violations);
            for (CandidateEducationFacts education : document.education()) {
                cursor = requireAfter(text, education.institution(), cursor, AtsViolationCategory.MISSING_EDUCATION_CONTENT, violations);
            }
        }

        if (!document.languages().isEmpty()) {
            cursor = requireAfter(text, CvSectionHeadings.LANGUAGES, cursor, AtsViolationCategory.MISSING_SECTION_HEADING, violations);
            for (CandidateLanguageFacts language : document.languages()) {
                requireAfter(text, language.name(), cursor, AtsViolationCategory.MISSING_LANGUAGE_CONTENT, violations);
            }
        }

        return new AtsVerificationResult(violations);
    }

    private static void verifyExperienceBeforeEducation(TailoredCvDocument document, String text, List<AtsVerificationViolation> violations) {
        if (document.experience().isEmpty() || document.education().isEmpty()) {
            return;
        }
        int experienceIndex = text.indexOf(CvSectionHeadings.PROFESSIONAL_EXPERIENCE);
        int educationIndex = text.indexOf(CvSectionHeadings.EDUCATION);
        if (experienceIndex < 0 || educationIndex < 0 || educationIndex < experienceIndex) {
            violations.add(new AtsVerificationViolation(AtsViolationCategory.EXPERIENCE_NOT_BEFORE_EDUCATION,
                    "'" + CvSectionHeadings.PROFESSIONAL_EXPERIENCE + "' must extract before '" + CvSectionHeadings.EDUCATION + "'"));
        }
    }

    /**
     * Production fix: the contact fields must be checked in the exact order the renderer's own
     * contact line actually prints them (location, phone, email - see {@code
     * PdfBoxApplicationMaterialDocumentRenderer#formatContactLine}), never an order chosen
     * independently here. This class's whole cursor design assumes "earlier check found earlier
     * text" - checking email before phone here while the renderer prints phone before email meant
     * the phone check could only ever search text {@code cursor.after} email, which is exactly
     * backwards, so it could never find a phone number word rendered before the email on the same
     * line. Dormant until a real header actually had both a phone and an email populated at once.
     */
    private static int verifyHeader(TailoredCvHeader header, String text, int cursor, List<AtsVerificationViolation> violations) {
        cursor = requireAfter(text, toUppercaseHeading(header.fullName()), cursor, AtsViolationCategory.MISSING_FULL_NAME, violations);
        cursor = requireAfter(text, header.cvHeadline(), cursor, AtsViolationCategory.MISSING_HEADLINE, violations);
        cursor = requireAfter(text, CvPhoneDisplay.format(header.phone()), cursor, AtsViolationCategory.MISSING_PHONE, violations);
        cursor = requireAfter(text, header.email(), cursor, AtsViolationCategory.MISSING_EMAIL, violations);
        cursor = requireAfter(text, CvUrlDisplay.linkedInDisplayText(header.linkedinUrl()), cursor, AtsViolationCategory.MISSING_LINKEDIN_TEXT, violations);
        return cursor;
    }

    private static int verifyCompany(TailoredCvCompany company, String text, int cursor, List<AtsVerificationViolation> violations) {
        cursor = requireAfter(text, toUppercaseHeading(company.name()), cursor, AtsViolationCategory.ORDERING_VIOLATION, violations);
        for (TailoredCvPosition position : company.positions()) {
            cursor = verifyPosition(position, text, cursor, violations);
        }
        return cursor;
    }

    private static int verifyPosition(TailoredCvPosition position, String text, int cursor, List<AtsVerificationViolation> violations) {
        cursor = requireAfter(text, position.title(), cursor, AtsViolationCategory.ORDERING_VIOLATION, violations);
        String dateRange = CvDateRangeFormatter.format(position.startDate(), position.endDate(), position.currentRole());
        cursor = requireAfter(text, dateRange, cursor, AtsViolationCategory.ORDERING_VIOLATION, violations);
        for (TailoredCvProject project : position.projects()) {
            cursor = verifyProject(project, text, cursor, violations);
        }
        return cursor;
    }

    private static int verifyProject(TailoredCvProject project, String text, int cursor, List<AtsVerificationViolation> violations) {
        return requireAfter(text, project.name(), cursor, AtsViolationCategory.ORDERING_VIOLATION, violations);
    }

    /**
     * Production fix: {@code PdfBoxApplicationMaterialDocumentRenderer} draws the candidate's full
     * name, each company name, and Mentoring's organization name in uppercase (a heading-style visual
     * choice matching the golden master's "SPRIBE"/"NETCRACKER TECHNOLOGY" headings) - every other
     * field (position/project/personal-project names, skills, education, languages) is drawn exactly
     * as given. Checking the raw-case value here for those three fields meant a real header/company/
     * mentoring-organization value could never be found in the uppercase-extracted text, the same
     * class of renderer/verifier drift already fixed once for phone/email ordering (see {@link
     * #verifyHeader}'s javadoc) - dormant here until a real end-to-end render+verify call exercised
     * it. {@code null}/blank pass through unchanged so {@link #requireAfter} keeps its own null-
     * tolerance.
     */
    private static String toUppercaseHeading(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private static void requireSkillTerm(String text, String skill, List<AtsVerificationViolation> violations) {
        if (skill != null && !skill.isBlank() && !text.contains(normalizeWhitespace(skill))) {
            violations.add(new AtsVerificationViolation(AtsViolationCategory.MISSING_SKILL_TERM, skill));
        }
    }

    /**
     * Searches for {@code needle} no earlier than {@code minIndex}; on success returns the index
     * just past the match, on failure/null/blank returns {@code minIndex} unchanged. {@code needle}
     * is whitespace-normalized before the search - {@code text} already is (see {@link #verify}) -
     * so a long sentence (e.g. a Mentoring bullet) that the renderer wraps onto more than one PDF
     * line, and whose text extraction therefore inserts a newline where the renderer drew a plain
     * space, is still found as one contiguous match. Production fix: this was
     * dormant everywhere else (headings/names/titles are all short enough to never wrap), but a real
     * Mentoring achievement bullet is long, ordinary prose - the first place this class ever checked
     * full free-text sentence content directly, not just short labels.
     */
    private static int requireAfter(String text, String needle, int minIndex, AtsViolationCategory category, List<AtsVerificationViolation> violations) {
        if (needle == null || needle.isBlank()) {
            return minIndex;
        }
        String normalizedNeedle = normalizeWhitespace(needle);
        int index = text.indexOf(normalizedNeedle, Math.max(minIndex, 0));
        if (index < 0) {
            violations.add(new AtsVerificationViolation(category, needle));
            return minIndex;
        }
        return index + normalizedNeedle.length();
    }

    /** Collapses every run of whitespace (including the line breaks PDFBox's text stripper inserts wherever a line visually wraps) into a single space - see {@link #requireAfter}'s javadoc. */
    private static String normalizeWhitespace(String value) {
        return value.replaceAll("\\s+", " ");
    }
}

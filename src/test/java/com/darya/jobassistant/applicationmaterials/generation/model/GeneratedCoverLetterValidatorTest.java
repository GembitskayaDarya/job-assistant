package com.darya.jobassistant.applicationmaterials.generation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidatecontext.applicationmaterials.CandidateContextForApplicationMaterialsProperties;
import com.darya.jobassistant.candidatecontext.applicationmaterials.CandidateContextForApplicationMaterialsSelector;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextForApplicationMaterials;
import com.darya.jobassistant.candidates.CandidateLanguageFacts;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerPosition;
import com.darya.jobassistant.careerhistory.aggregate.CareerResponsibility;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Sprint 11 Big Block 7 (renamed from {@code GeneratedApplicationMaterialsValidatorTest}, trimmed to
 * cover-letter-only coverage matching {@link GeneratedCoverLetterValidator}'s new, narrower
 * contract - the CV half of the original combined validator was removed entirely, replaced by
 * {@code CvTailoringValidator} (already tested from Block 6).
 */
class GeneratedCoverLetterValidatorTest {

    private static final CandidateContextForApplicationMaterialsProperties GENEROUS =
            new CandidateContextForApplicationMaterialsProperties(8, 12, 6, 6, 6, 6, 15, 1500, 20000);

    private final UUID companyId = UUID.randomUUID();
    private final UUID positionId = UUID.randomUUID();
    private final UUID responsibilityId = UUID.randomUUID();

    // ==================== Cover letter provenance ====================

    @Test
    void validate_paragraphWithNoSourceIds_isAccepted() {
        CandidateContextForApplicationMaterials context = simpleContext();
        GeneratedCoverLetter raw = new GeneratedCoverLetter(
                null, List.of(new GeneratedCoverLetterParagraph("Purely connective wording.", List.of())), "Closing");

        GeneratedCoverLetter validated = GeneratedCoverLetterValidator.validate(raw, context);

        assertThat(validated.paragraphs().get(0).sourceIds()).isEmpty();
    }

    @Test
    void validate_paragraphWithInvalidSourceId_isRejected() {
        CandidateContextForApplicationMaterials context = simpleContext();
        GeneratedCoverLetter raw = new GeneratedCoverLetter(
                null, List.of(new GeneratedCoverLetterParagraph("Factual claim.", List.of(UUID.randomUUID()))), "Closing");

        assertThatThrownBy(() -> GeneratedCoverLetterValidator.validate(raw, context))
                .isInstanceOf(ApplicationMaterialsValidationException.class)
                .hasMessageContaining("source id");
    }

    @Test
    void validate_paragraphWithValidSourceId_isAccepted() {
        CandidateContextForApplicationMaterials context = simpleContext();
        GeneratedCoverLetter raw = new GeneratedCoverLetter(
                null, List.of(new GeneratedCoverLetterParagraph("Factual claim.", List.of(responsibilityId))), "Closing");

        GeneratedCoverLetter validated = GeneratedCoverLetterValidator.validate(raw, context);

        assertThat(validated.paragraphs().get(0).sourceIds()).containsExactly(responsibilityId);
    }

    @Test
    void validate_duplicateSourceIdsInOneParagraph_areNormalizedNotRejected() {
        CandidateContextForApplicationMaterials context = simpleContext();
        GeneratedCoverLetter raw = new GeneratedCoverLetter(
                null, List.of(new GeneratedCoverLetterParagraph("Factual claim.", List.of(responsibilityId, responsibilityId))), "Closing");

        GeneratedCoverLetter validated = GeneratedCoverLetterValidator.validate(raw, context);

        assertThat(validated.paragraphs().get(0).sourceIds()).containsExactly(responsibilityId);
    }

    @Test
    void validate_greeting_isPreservedWhenPresent() {
        CandidateContextForApplicationMaterials context = simpleContext();
        GeneratedCoverLetter raw = new GeneratedCoverLetter(
                "Dear Hiring Manager,", List.of(new GeneratedCoverLetterParagraph("Body.", List.of())), "Closing");

        GeneratedCoverLetter validated = GeneratedCoverLetterValidator.validate(raw, context);

        assertThat(validated.greeting()).isEqualTo("Dear Hiring Manager,");
    }

    @Test
    void validate_blankGreeting_isNormalizedToNull() {
        CandidateContextForApplicationMaterials context = simpleContext();
        GeneratedCoverLetter raw = new GeneratedCoverLetter(
                "   ", List.of(new GeneratedCoverLetterParagraph("Body.", List.of())), "Closing");

        GeneratedCoverLetter validated = GeneratedCoverLetterValidator.validate(raw, context);

        assertThat(validated.greeting()).isNull();
    }

    // ==================== Provenance-leak defense in depth (production fix) ====================

    /**
     * The exact real-production leak shape: a generated paragraph's text ending in a literal
     * {@code "(sourceIds: [uuid, ...])"} suffix even though its own separate {@code sourceIds} field
     * was empty. The primary fix is architectural ({@code CoverLetterEvidenceReferenceIndex} - the AI
     * is no longer shown or asked to return a raw UUID at all), so this should never legitimately
     * happen again; this test proves the independent safety net still catches it if it somehow did.
     */
    @Test
    void validate_paragraphTextLeakingSourceIdsSuffix_isRejected() {
        CandidateContextForApplicationMaterials context = simpleContext();
        String leaked = "I optimized performance to support high load (sourceIds: [" + UUID.randomUUID() + "]).";
        GeneratedCoverLetter raw = new GeneratedCoverLetter(null, List.of(new GeneratedCoverLetterParagraph(leaked, List.of())), "Closing");

        assertThatThrownBy(() -> GeneratedCoverLetterValidator.validate(raw, context))
                .isInstanceOf(ApplicationMaterialsValidationException.class)
                .hasMessageContaining("provenance");
    }

    @Test
    void validate_paragraphTextLeakingReferenceToken_isRejected() {
        CandidateContextForApplicationMaterials context = simpleContext();
        String leaked = "I led the migration (ref: EVIDENCE_003).";
        GeneratedCoverLetter raw = new GeneratedCoverLetter(null, List.of(new GeneratedCoverLetterParagraph(leaked, List.of())), "Closing");

        assertThatThrownBy(() -> GeneratedCoverLetterValidator.validate(raw, context))
                .isInstanceOf(ApplicationMaterialsValidationException.class)
                .hasMessageContaining("provenance");
    }

    @Test
    void validate_paragraphTextLeakingRawUuid_isRejected() {
        CandidateContextForApplicationMaterials context = simpleContext();
        String leaked = "I led the migration (" + UUID.randomUUID() + ").";
        GeneratedCoverLetter raw = new GeneratedCoverLetter(null, List.of(new GeneratedCoverLetterParagraph(leaked, List.of())), "Closing");

        assertThatThrownBy(() -> GeneratedCoverLetterValidator.validate(raw, context))
                .isInstanceOf(ApplicationMaterialsValidationException.class)
                .hasMessageContaining("provenance");
    }

    @Test
    void validate_greetingOrClosingLeakingProvenanceSyntax_isRejected() {
        CandidateContextForApplicationMaterials context = simpleContext();
        GeneratedCoverLetter raw = new GeneratedCoverLetter(
                "Dear Hiring Team (sourceIds: []),", List.of(new GeneratedCoverLetterParagraph("Body.", List.of())), "Closing");

        assertThatThrownBy(() -> GeneratedCoverLetterValidator.validate(raw, context))
                .isInstanceOf(ApplicationMaterialsValidationException.class)
                .hasMessageContaining("provenance");
    }

    @Test
    void validate_ordinaryProseWithoutProvenanceSyntax_isAccepted() {
        CandidateContextForApplicationMaterials context = simpleContext();
        GeneratedCoverLetter raw = new GeneratedCoverLetter(
                null, List.of(new GeneratedCoverLetterParagraph(
                        "I led backend service development, focusing on reliability and performance.", List.of())),
                "Closing");

        GeneratedCoverLetter validated = GeneratedCoverLetterValidator.validate(raw, context);

        assertThat(validated.paragraphs().get(0).text()).doesNotContain("sourceIds", "sourceRefs", "EVIDENCE_");
    }

    // ==================== Structural checks ====================

    @Test
    void validate_nullResponse_isRejected() {
        assertThatThrownBy(() -> GeneratedCoverLetterValidator.validate(null, simpleContext()))
                .isInstanceOf(ApplicationMaterialsValidationException.class);
    }

    @Test
    void validate_tooManyParagraphs_isRejected() {
        CandidateContextForApplicationMaterials context = simpleContext();
        List<GeneratedCoverLetterParagraph> paragraphs = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            paragraphs.add(new GeneratedCoverLetterParagraph("Paragraph " + i, List.of()));
        }
        GeneratedCoverLetter raw = new GeneratedCoverLetter(null, paragraphs, "Closing");

        assertThatThrownBy(() -> GeneratedCoverLetterValidator.validate(raw, context))
                .isInstanceOf(ApplicationMaterialsValidationException.class)
                .hasMessageContaining("too many cover letter paragraphs");
    }

    @Test
    void validate_paragraphTextExceedingMaxLength_isRejected() {
        CandidateContextForApplicationMaterials context = simpleContext();
        String tooLong = "x".repeat(2001);
        GeneratedCoverLetter raw = new GeneratedCoverLetter(null, List.of(new GeneratedCoverLetterParagraph(tooLong, List.of())), "Closing");

        assertThatThrownBy(() -> GeneratedCoverLetterValidator.validate(raw, context))
                .isInstanceOf(ApplicationMaterialsValidationException.class)
                .hasMessageContaining("paragraph text");
    }

    // ==================== Fixtures ====================

    private CandidateContextForApplicationMaterials simpleContext() {
        CareerPosition position = new CareerPosition(positionId, "Backend Engineer", null, null, null,
                LocalDate.of(2020, 1, 1), null, true, null, 0,
                List.of(new CareerResponsibility(responsibilityId, "Built backend services", 0)), List.of(), List.of());
        CareerCompany company = new CareerCompany(companyId, "Acme", null, null, null, null, 0, List.of(position));
        CareerHistoryAggregate careerHistory = new CareerHistoryAggregate(UUID.randomUUID(), UUID.randomUUID(), List.of(company), 0L);
        CandidateContextSnapshot snapshot = new CandidateContextSnapshot(
                UUID.randomUUID(), "primary", 0L, validProfile(), Optional.of(careerHistory));
        return new CandidateContextForApplicationMaterialsSelector(GENEROUS).select(snapshot, vacancy());
    }

    private CandidateProfileFacts validProfile() {
        return new CandidateProfileFacts("Senior Java Backend Engineer", "Senior",
                List.of(new CandidateSkillFacts("Java", null, null, SkillProficiency.STRONG)),
                List.of(new CandidateLanguageFacts("en", null)), 6,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null));
    }

    private JobOffer vacancy() {
        return new JobOffer("job-1", "Backend Engineer", "Acme Corp", "Remote", null,
                "We need a backend engineer.", "https://example.com/job-1", "test");
    }
}

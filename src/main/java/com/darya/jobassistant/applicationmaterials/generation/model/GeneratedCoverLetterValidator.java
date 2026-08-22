package com.darya.jobassistant.applicationmaterials.generation.model;

import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextForApplicationMaterials;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.SelectedCareerAchievement;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.SelectedCareerCompany;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.SelectedCareerPosition;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.SelectedCareerProject;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.SelectedCareerResponsibility;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.SelectedCareerTechnology;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Sprint 10 Step 3 (Sprint 11 Big Block 7 correction, renamed from {@code
 * GeneratedApplicationMaterialsValidator}): deterministic, AI-free, database-free validation of a
 * raw {@link ApplicationMaterialsAiPort#generate} cover-letter response against the exact bounded
 * {@link CandidateContextForApplicationMaterials} used to produce it - the primary anti-
 * hallucination defense, alongside the bounded prompt input and the strict system prompt. Never
 * attempts general semantic hallucination detection (that would require a second AI call,
 * explicitly out of scope); everything checked here is a structural or referential fact this class
 * can decide on its own.
 *
 * <p>The CV-validation half of the original combined validator was removed in Sprint 11 Big Block 7
 * - CV content is no longer part of this AI response at all (see {@code
 * ApplicationMaterialsAiPort}'s javadoc); {@code CvTailoringValidator} is its Sprint 11 replacement,
 * validating a structurally different (id-selection, not free-form-bullet) contract.
 *
 * <h2>What this validates</h2>
 *
 * Every {@link GeneratedCoverLetterParagraph#sourceIds()} id (when present - paragraphs may
 * legitimately carry none) exists anywhere in the selected context, plus structural bounds (list
 * sizes, field lengths) and non-blank required text. Duplicate source-reference ids within one
 * paragraph are normalized (de-duplicated, first occurrence kept) rather than rejected - a repeated
 * reference is a harmless AI redundancy, not evidence of fabrication.
 */
public final class GeneratedCoverLetterValidator {

    private static final int MAX_COVER_LETTER_PARAGRAPHS = 8;
    private static final int MAX_TEXT_LENGTH = 2000;

    /**
     * Defense-in-depth (production hardening): rejects paragraph/greeting/closing text that leaks
     * internal provenance/reference syntax - the exact real-production shape this exists for was a
     * generated paragraph ending in a literal {@code "(sourceIds: [uuid, uuid, ...])"} suffix. The
     * primary fix is architectural (see {@code CoverLetterEvidenceReferenceIndex} - the model is no
     * longer shown or asked to return a raw UUID at all), so this pattern should never legitimately
     * match; it exists purely as a second, independent safety net in case a future prompt/schema
     * regression reintroduces the leak. Matches, case-insensitively: the words "sourceRefs"/
     * "sourceIds", an {@code EVIDENCE_*}-shaped reference token, or a raw UUID.
     */
    private static final Pattern PROVENANCE_LEAK_PATTERN = Pattern.compile(
            "(?i)sourceRefs|sourceIds|EVIDENCE_\\d+|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private GeneratedCoverLetterValidator() {
    }

    public static GeneratedCoverLetter validate(GeneratedCoverLetter raw, CandidateContextForApplicationMaterials context) {
        if (raw == null) {
            throw new ApplicationMaterialsValidationException("AI provider returned no generated cover letter");
        }
        Set<UUID> allNodeIds = allNodeIds(context);

        String greeting = raw.greeting() == null || raw.greeting().isBlank() ? null : raw.greeting();
        requireMaxLength(greeting, "cover letter greeting", MAX_TEXT_LENGTH);
        requireMaxLength(raw.closing(), "cover letter closing", MAX_TEXT_LENGTH);
        requireNoProvenanceLeak(greeting, "cover letter greeting");
        requireNoProvenanceLeak(raw.closing(), "cover letter closing");

        List<GeneratedCoverLetterParagraph> paragraphs = raw.paragraphs();
        if (paragraphs.size() > MAX_COVER_LETTER_PARAGRAPHS) {
            throw new ApplicationMaterialsValidationException(
                    "AI provider returned too many cover letter paragraphs (" + paragraphs.size() + ")");
        }
        List<GeneratedCoverLetterParagraph> validatedParagraphs = new ArrayList<>();
        for (GeneratedCoverLetterParagraph paragraph : paragraphs) {
            requireMaxLength(paragraph.text(), "cover letter paragraph text", MAX_TEXT_LENGTH);
            requireNoProvenanceLeak(paragraph.text(), "cover letter paragraph text");
            List<UUID> normalizedSourceIds = new ArrayList<>(new LinkedHashSet<>(paragraph.sourceIds()));
            for (UUID sourceId : normalizedSourceIds) {
                if (!allNodeIds.contains(sourceId)) {
                    throw new ApplicationMaterialsValidationException(
                            "AI provider referenced a source id outside the selected candidate context: " + sourceId);
                }
            }
            validatedParagraphs.add(new GeneratedCoverLetterParagraph(paragraph.text(), normalizedSourceIds));
        }
        return new GeneratedCoverLetter(greeting, validatedParagraphs, raw.closing());
    }

    /**
     * Defense-in-depth check - see {@link #PROVENANCE_LEAK_PATTERN}'s javadoc. Never logs or includes
     * {@code value} itself in the thrown exception's message (candidate/vacancy content must never
     * appear in a message a caller might log or persist) - only the safe field name.
     */
    private static void requireNoProvenanceLeak(String value, String field) {
        if (value != null && PROVENANCE_LEAK_PATTERN.matcher(value).find()) {
            throw new ApplicationMaterialsValidationException(
                    "AI provider leaked internal reference/provenance syntax into " + field);
        }
    }

    private static void requireMaxLength(String value, String field, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new ApplicationMaterialsValidationException(
                    "AI provider returned a " + field + " exceeding " + maxLength + " characters");
        }
    }

    private static Set<UUID> allNodeIds(CandidateContextForApplicationMaterials context) {
        Set<UUID> allNodeIds = new HashSet<>();
        for (SelectedCareerCompany company : context.selectedCompanies()) {
            allNodeIds.add(company.careerCompanyId());
            for (SelectedCareerPosition position : company.positions()) {
                allNodeIds.add(position.careerPositionId());
                position.responsibilities().stream().map(SelectedCareerResponsibility::careerResponsibilityId).forEach(allNodeIds::add);
                position.achievements().stream().map(SelectedCareerAchievement::careerAchievementId).forEach(allNodeIds::add);
                for (SelectedCareerProject project : position.projects()) {
                    allNodeIds.add(project.careerProjectId());
                    project.responsibilities().stream().map(SelectedCareerResponsibility::careerResponsibilityId).forEach(allNodeIds::add);
                    project.achievements().stream().map(SelectedCareerAchievement::careerAchievementId).forEach(allNodeIds::add);
                    project.technologies().stream().map(SelectedCareerTechnology::careerTechnologyId).forEach(allNodeIds::add);
                }
            }
        }
        return allNodeIds;
    }
}

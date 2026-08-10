package com.darya.jobassistant.applicationmaterials.generation.model;

import com.darya.jobassistant.candidates.CandidateSkill;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextForApplicationMaterials;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.SelectedCareerCompany;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.SelectedCareerPosition;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.SelectedCareerProject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sprint 10 Step 3: deterministic, AI-free, database-free validation of a raw {@link
 * ApplicationMaterialsAiPort#generate} response against the exact bounded {@link
 * CandidateContextForApplicationMaterials} used to produce it - the primary anti-hallucination
 * defense, alongside the bounded prompt input and the strict system prompt. Never attempts general
 * semantic hallucination detection (that would require a second AI call, explicitly out of scope);
 * everything checked here is a structural or referential fact this class can decide on its own.
 *
 * <h2>What this validates</h2>
 *
 * <ul>
 *   <li>Every {@link GeneratedCvExperience#careerPositionId()} is one of the positions actually
 *       selected into {@code context} - never a position outside it, never a duplicate.
 *   <li>Every {@link GeneratedExperienceBullet#sourceIds()} id exists among the referenced
 *       position's own facts (its responsibilities/achievements, or one of its selected projects'
 *       own responsibilities/achievements/technologies) - never a fact belonging to a different
 *       position, and never a fact outside the selected context entirely.
 *   <li>Every {@link GeneratedCoverLetterParagraph#sourceIds()} id (when present - paragraphs may
 *       legitimately carry none) exists anywhere in the selected context.
 *   <li>Every {@link GeneratedCvSkill#name()} matches either a {@code CandidateProfile} skill or a
 *       technology actually present in the selected Career History evidence - a name matching
 *       neither (e.g. a vacancy-only technology) is rejected. {@link GeneratedCvSkill#proficiency()}
 *       from the raw response is never trusted - it is always replaced with the candidate's own
 *       recorded proficiency (or {@code null} for a Career-History-only technology with none).
 *   <li>Structural bounds (list sizes, field lengths) and non-blank required text.
 * </ul>
 *
 * <p>Duplicate source-reference ids (within one bullet/paragraph, or a skill name repeated in the
 * skill list) are normalized (de-duplicated, first occurrence kept) rather than rejected - a
 * repeated reference is a harmless AI redundancy, not evidence of fabrication. A duplicate {@code
 * careerPositionId} across two different experiences, by contrast, is rejected: two experience
 * entries for the same job is a structural anomaly a CV should never contain.
 */
public final class GeneratedApplicationMaterialsValidator {

    private static final int MAX_SKILLS = 20;
    private static final int MAX_EXPERIENCES = 12;
    private static final int MAX_BULLETS_PER_EXPERIENCE = 8;
    private static final int MAX_COVER_LETTER_PARAGRAPHS = 8;
    private static final int MAX_TEXT_LENGTH = 2000;

    private GeneratedApplicationMaterialsValidator() {
    }

    public static GeneratedApplicationMaterials validate(
            GeneratedApplicationMaterials raw, CandidateContextForApplicationMaterials context) {
        if (raw == null) {
            throw new ApplicationMaterialsValidationException("AI provider returned no generated application materials");
        }
        ContextIndex index = ContextIndex.build(context);
        GeneratedCv validatedCv = validateCv(raw.cv(), index);
        GeneratedCoverLetter validatedCoverLetter = validateCoverLetter(raw.coverLetter(), index);
        return new GeneratedApplicationMaterials(validatedCv, validatedCoverLetter);
    }

    // ==================== CV ====================

    private static GeneratedCv validateCv(GeneratedCv cv, ContextIndex index) {
        if (cv == null) {
            throw new ApplicationMaterialsValidationException("AI provider returned no generated CV");
        }
        requireMaxLength(cv.headline(), "CV headline", MAX_TEXT_LENGTH);
        requireMaxLength(cv.professionalSummary(), "CV professional summary", MAX_TEXT_LENGTH);

        List<GeneratedCvSkill> validatedSkills = validateSkills(cv.skills(), index);
        List<GeneratedCvExperience> validatedExperiences = validateExperiences(cv.experiences(), index);

        return new GeneratedCv(cv.headline(), cv.professionalSummary(), validatedSkills, validatedExperiences, cv.languages());
    }

    private static List<GeneratedCvSkill> validateSkills(List<GeneratedCvSkill> skills, ContextIndex index) {
        if (skills.size() > MAX_SKILLS) {
            throw new ApplicationMaterialsValidationException(
                    "AI provider returned too many CV skills (" + skills.size() + ")");
        }
        List<GeneratedCvSkill> resolved = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        for (GeneratedCvSkill skill : skills) {
            String normalizedName = normalize(skill.name());
            if (!seenNames.add(normalizedName)) {
                continue;
            }
            resolved.add(resolveSkill(skill.name(), normalizedName, index));
        }
        return resolved;
    }

    private static GeneratedCvSkill resolveSkill(String rawName, String normalizedName, ContextIndex index) {
        SkillProficiency profileProficiency = index.profileSkillProficiencyByNormalizedName().get(normalizedName);
        if (profileProficiency != null) {
            return new GeneratedCvSkill(rawName, profileProficiency);
        }
        if (index.careerHistoryTechnologyNormalizedNames().contains(normalizedName)) {
            return new GeneratedCvSkill(rawName, null);
        }
        throw new ApplicationMaterialsValidationException(
                "AI provider returned a CV skill not present in the candidate profile or selected career history: '" + rawName + "'");
    }

    /**
     * An empty list is accepted, not rejected: a candidate with no Career History at all
     * legitimately has nothing to reference (see {@code CareerHistoryAvailability#NOT_PROVIDED}/
     * {@code EMPTY}), and even with evidence available the AI may reasonably choose to feature none
     * of it - "choose which supported experience to emphasize" permits emphasizing nothing. An
     * empty experience list is never itself a hallucination risk.
     */
    private static List<GeneratedCvExperience> validateExperiences(List<GeneratedCvExperience> experiences, ContextIndex index) {
        if (experiences.size() > MAX_EXPERIENCES) {
            throw new ApplicationMaterialsValidationException(
                    "AI provider returned too many CV experiences (" + experiences.size() + ")");
        }
        List<GeneratedCvExperience> validated = new ArrayList<>();
        Set<UUID> seenPositionIds = new HashSet<>();
        for (GeneratedCvExperience experience : experiences) {
            UUID positionId = experience.careerPositionId();
            Set<UUID> allowedSourceIds = index.allowedSourceIdsByPositionId().get(positionId);
            if (allowedSourceIds == null) {
                throw new ApplicationMaterialsValidationException(
                        "AI provider referenced a careerPositionId that is not part of the selected candidate context: " + positionId);
            }
            if (!seenPositionIds.add(positionId)) {
                throw new ApplicationMaterialsValidationException(
                        "AI provider returned more than one CV experience for the same careerPositionId: " + positionId);
            }
            validated.add(new GeneratedCvExperience(positionId, validateBullets(experience.bullets(), allowedSourceIds)));
        }
        return validated;
    }

    private static List<GeneratedExperienceBullet> validateBullets(List<GeneratedExperienceBullet> bullets, Set<UUID> allowedSourceIds) {
        if (bullets.size() > MAX_BULLETS_PER_EXPERIENCE) {
            throw new ApplicationMaterialsValidationException(
                    "AI provider returned too many bullets for one CV experience (" + bullets.size() + ")");
        }
        List<GeneratedExperienceBullet> validated = new ArrayList<>();
        for (GeneratedExperienceBullet bullet : bullets) {
            requireMaxLength(bullet.text(), "CV experience bullet text", MAX_TEXT_LENGTH);
            List<UUID> normalizedSourceIds = new ArrayList<>(new LinkedHashSet<>(bullet.sourceIds()));
            for (UUID sourceId : normalizedSourceIds) {
                if (!allowedSourceIds.contains(sourceId)) {
                    throw new ApplicationMaterialsValidationException(
                            "AI provider referenced a source id outside this experience's own selected candidate context: " + sourceId);
                }
            }
            validated.add(new GeneratedExperienceBullet(bullet.text(), normalizedSourceIds));
        }
        return validated;
    }

    // ==================== Cover letter ====================

    private static GeneratedCoverLetter validateCoverLetter(GeneratedCoverLetter coverLetter, ContextIndex index) {
        if (coverLetter == null) {
            throw new ApplicationMaterialsValidationException("AI provider returned no generated cover letter");
        }
        String greeting = coverLetter.greeting() == null || coverLetter.greeting().isBlank() ? null : coverLetter.greeting();
        requireMaxLength(greeting, "cover letter greeting", MAX_TEXT_LENGTH);
        requireMaxLength(coverLetter.closing(), "cover letter closing", MAX_TEXT_LENGTH);

        List<GeneratedCoverLetterParagraph> paragraphs = coverLetter.paragraphs();
        if (paragraphs.size() > MAX_COVER_LETTER_PARAGRAPHS) {
            throw new ApplicationMaterialsValidationException(
                    "AI provider returned too many cover letter paragraphs (" + paragraphs.size() + ")");
        }
        List<GeneratedCoverLetterParagraph> validatedParagraphs = new ArrayList<>();
        for (GeneratedCoverLetterParagraph paragraph : paragraphs) {
            requireMaxLength(paragraph.text(), "cover letter paragraph text", MAX_TEXT_LENGTH);
            List<UUID> normalizedSourceIds = new ArrayList<>(new LinkedHashSet<>(paragraph.sourceIds()));
            for (UUID sourceId : normalizedSourceIds) {
                if (!index.allNodeIds().contains(sourceId)) {
                    throw new ApplicationMaterialsValidationException(
                            "AI provider referenced a source id outside the selected candidate context: " + sourceId);
                }
            }
            validatedParagraphs.add(new GeneratedCoverLetterParagraph(paragraph.text(), normalizedSourceIds));
        }
        return new GeneratedCoverLetter(greeting, validatedParagraphs, coverLetter.closing());
    }

    // ==================== Shared checks ====================

    private static void requireMaxLength(String value, String field, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new ApplicationMaterialsValidationException(
                    "AI provider returned a " + field + " exceeding " + maxLength + " characters");
        }
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Built once per {@link #validate} call from {@code context} - every lookup structure the
     * validator needs, computed up front so the per-field validation methods stay simple existence
     * checks against an already-built index.
     */
    private record ContextIndex(
            Set<UUID> allNodeIds,
            Map<UUID, Set<UUID>> allowedSourceIdsByPositionId,
            Map<String, SkillProficiency> profileSkillProficiencyByNormalizedName,
            Set<String> careerHistoryTechnologyNormalizedNames
    ) {

        static ContextIndex build(CandidateContextForApplicationMaterials context) {
            Set<UUID> allNodeIds = new HashSet<>();
            Map<UUID, Set<UUID>> allowedSourceIdsByPositionId = new HashMap<>();
            Set<String> technologyNames = new HashSet<>();

            for (SelectedCareerCompany company : context.selectedCompanies()) {
                allNodeIds.add(company.careerCompanyId());
                for (SelectedCareerPosition position : company.positions()) {
                    Set<UUID> allowed = new HashSet<>();
                    allowed.add(position.careerPositionId());
                    position.responsibilities().forEach(r -> allowed.add(r.careerResponsibilityId()));
                    position.achievements().forEach(a -> allowed.add(a.careerAchievementId()));
                    for (SelectedCareerProject project : position.projects()) {
                        allowed.add(project.careerProjectId());
                        project.responsibilities().forEach(r -> allowed.add(r.careerResponsibilityId()));
                        project.achievements().forEach(a -> allowed.add(a.careerAchievementId()));
                        project.technologies().forEach(t -> {
                            allowed.add(t.careerTechnologyId());
                            technologyNames.add(normalize(t.name()));
                        });
                    }
                    allNodeIds.addAll(allowed);
                    allowedSourceIdsByPositionId.put(position.careerPositionId(), allowed);
                }
            }

            Map<String, SkillProficiency> profileSkills = new HashMap<>();
            for (CandidateSkill skill : context.candidateProfile().skills()) {
                profileSkills.put(normalize(skill.name()), skill.proficiency());
            }

            return new ContextIndex(allNodeIds, allowedSourceIdsByPositionId, profileSkills, technologyNames);
        }
    }
}

package com.darya.jobassistant.candidatecontext.cv.tailoring;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Sprint 11 Final CV Policy: the complete AI contract for CV tailoring, as of this block - AI
 * chooses which factual {@code CandidateSkillFacts#candidateSkillId()} entries are relevant to one
 * vacancy, and in what order. Nothing else about a CV is AI-controlled any more (see {@code
 * CvTailoringUseCase}'s class javadoc): Professional Summary, career history, and Personal Project
 * content are all deterministic, manually-approved baseline content resolved by {@code
 * BaselineCvSelectionResolver}, never by this result.
 *
 * <p>{@link #orderedSkillIds} is the AI's raw selection, not yet canonicalized/capped - {@code
 * CvSkillCanonicalizationPolicy} applies the deterministic presentation policy (collapse narrow
 * framework/module variants into their canonical family, dedupe, cap to the recruiter-facing
 * maximum) before this ever reaches {@link CvTailoringResult#orderedSkillIds()}.
 */
public record CvSkillTailoringResult(List<UUID> orderedSkillIds) {

    public CvSkillTailoringResult {
        requireNoNullOrDuplicateSkillIds(orderedSkillIds == null ? List.of() : orderedSkillIds);
        orderedSkillIds = orderedSkillIds == null ? List.of() : List.copyOf(orderedSkillIds);
    }

    private static void requireNoNullOrDuplicateSkillIds(List<UUID> skillIds) {
        Set<UUID> seen = new HashSet<>();
        for (UUID skillId : skillIds) {
            if (skillId == null) {
                throw new IllegalArgumentException("Skill tailoring result orderedSkillIds must not contain a null id");
            }
            if (!seen.add(skillId)) {
                throw new IllegalArgumentException("Duplicate skill reference in skill tailoring result: " + skillId);
            }
        }
    }
}

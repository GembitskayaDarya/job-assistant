package com.darya.jobassistant.candidates.migration;

import com.darya.jobassistant.candidates.CandidateLanguageFacts;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;
import java.util.List;

/**
 * Assembles the complete {@link CandidateProfileFacts} into the bounded, YAML-shaped,
 * analysis-ready {@link CandidateProfile} - the intentionally lossy narrowing step for vacancy
 * analysis specifically (drops {@link CandidateSkillFacts#category} and every language's {@link
 * CandidateLanguageFacts#proficiency}). See {@link CandidateProfileYamlImportMapper} for the
 * opposite (migration) direction; the two are deliberately separate classes.
 *
 * <p>Sprint 11 Step 1 correction: {@link #toAnalysisProfile(CandidateProfileAggregate)} - kept for
 * {@code PersistentCandidateProfileProvider}/{@code CandidateProfileParityVerifier}, which still
 * legitimately need the aggregate-to-analysis-profile shortcut - is now a pure composition of
 * {@link CandidateProfileFactsAssembler#toProfileFacts} (the lossless "full facts" stage) followed
 * by {@link #toAnalysisProfile(CandidateProfileFacts)} (this class's actual narrowing logic), so the
 * two can never drift out of sync. {@code
 * com.darya.jobassistant.candidatecontext.runtime.PersistentCandidateContextProvider} - the common
 * {@code CandidateContextSnapshot} builder - deliberately calls {@code toProfileFacts} directly and
 * never this class at all: the common candidate context must carry the complete facts, with any
 * analysis-specific narrowing happening only at the analysis boundary itself ({@code
 * CandidateContextForAnalysisSelector}/{@code CandidateContextForApplicationMaterialsSelector}).
 *
 * <p>No persistence id or version leaks into the result: {@code CandidateProfile} has no such
 * fields at all. Framework-free and stateless: no repository calls, no Spring dependencies.
 */
public final class CandidateProfileAnalysisAssembler {

    private CandidateProfileAnalysisAssembler() {
    }

    public static CandidateProfile toAnalysisProfile(CandidateProfileAggregate aggregate) {
        if (aggregate == null) {
            throw new IllegalArgumentException("Source candidate profile aggregate must not be null");
        }
        return toAnalysisProfile(CandidateProfileFactsAssembler.toProfileFacts(aggregate));
    }

    public static CandidateProfile toAnalysisProfile(CandidateProfileFacts facts) {
        if (facts == null) {
            throw new IllegalArgumentException("Source candidate profile facts must not be null");
        }
        return new CandidateProfile(
                facts.targetRole(),
                facts.targetSeniority(),
                toSkills(facts.skills()),
                toLanguageNames(facts.languages()),
                facts.experienceYears(),
                facts.preferences());
    }

    private static List<com.darya.jobassistant.candidates.CandidateSkill> toSkills(List<CandidateSkillFacts> skills) {
        return skills.stream()
                .map(skill -> new com.darya.jobassistant.candidates.CandidateSkill(skill.name(), skill.proficiency(), skill.note()))
                .toList();
    }

    private static List<String> toLanguageNames(List<CandidateLanguageFacts> languages) {
        return languages.stream().map(CandidateLanguageFacts::name).toList();
    }
}

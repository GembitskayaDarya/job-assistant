package com.darya.jobassistant.candidatecontext;

import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sprint 9 Step 8, corrected in Sprint 11 Step 1: framework-free, immutable snapshot of the
 * complete candidate facts every downstream use case may draw on - {@link CandidateProfileFacts}
 * plus an optional {@link CareerHistoryAggregate}, read together inside one consistency boundary by
 * {@link CandidateContextProvider#loadCurrentContext()}. This is reusable candidate state, not an
 * AI prompt and not itself constrained by any single use case's needs.
 *
 * <p>Originally carried the vacancy-analysis-bounded {@code candidates.CandidateProfile} here
 * directly, which silently dropped skill category and language proficiency before any downstream
 * consumer - including non-analysis ones such as {@code candidatecontext.cv.CvSourceSnapshotFactory}
 * - ever saw them. {@link #candidateProfile} now carries the complete, lossless {@link
 * CandidateProfileFacts} instead; each bounded, vacancy-specific projection (see {@code
 * candidatecontext.analysis.CandidateContextForAnalysis}, {@code
 * candidatecontext.applicationmaterials.model.CandidateContextForApplicationMaterials}) narrows it
 * explicitly at its own selector boundary via {@code
 * candidates.migration.CandidateProfileAnalysisAssembler#toAnalysisProfile(CandidateProfileFacts)}.
 *
 * <p>Never exposes a JPA entity or a {@code @ConfigurationProperties} object. {@link #careerHistory}
 * is {@link Optional#empty()} when Career History has never been imported for this candidate -
 * that is a fully valid, non-error state (see {@link CareerHistoryAvailability#NOT_PROVIDED}).
 *
 * <p>{@link #personalProjects} (Sprint 11 Step 5) is a sibling factual source read from its own
 * independent aggregate ({@code personalprojects.aggregate.PersonalProjectRepositoryPort}), the
 * same composition pattern {@link #careerHistory} already uses - never folded into {@link
 * #candidateProfile}, which stays strictly the lossless projection of {@code
 * CandidateProfileAggregate} alone. Always a valid, non-null (possibly empty) list - unlike Career
 * History, there is no separate "never provided" state to distinguish: each Personal Project is
 * its own independently-saved aggregate root, so an empty list unambiguously means "this candidate
 * currently has zero Personal Projects," the same as an empty skills/languages list.
 */
public record CandidateContextSnapshot(
        UUID candidateProfileId,
        String profileKey,
        long candidateProfileVersion,
        CandidateProfileFacts candidateProfile,
        Optional<CareerHistoryAggregate> careerHistory,
        List<PersonalProject> personalProjects
) {

    public CandidateContextSnapshot {
        if (candidateProfileId == null) {
            throw new IllegalArgumentException("Candidate context candidate profile id must not be null");
        }
        if (profileKey == null || profileKey.isBlank()) {
            throw new IllegalArgumentException("Candidate context profile key must not be blank");
        }
        if (candidateProfileVersion < 0) {
            throw new IllegalArgumentException("Candidate context candidate profile version must not be negative");
        }
        if (candidateProfile == null) {
            throw new IllegalArgumentException("Candidate context candidate profile must not be null");
        }
        careerHistory = careerHistory == null ? Optional.empty() : careerHistory;
        personalProjects = personalProjects == null ? List.of() : List.copyOf(personalProjects);
    }

    /**
     * Convenience constructor matching this type's pre-Sprint-11-Step-5 shape - defaults {@link
     * #personalProjects} to empty, so the many existing call sites built before Personal Projects
     * existed keep compiling unchanged.
     */
    public CandidateContextSnapshot(
            UUID candidateProfileId, String profileKey, long candidateProfileVersion,
            CandidateProfileFacts candidateProfile, Optional<CareerHistoryAggregate> careerHistory) {
        this(candidateProfileId, profileKey, candidateProfileVersion, candidateProfile, careerHistory, List.of());
    }

    /**
     * Derives the three-state availability {@link CareerHistoryAvailability} from {@link
     * #careerHistory} - the one place this distinction is computed, so callers never re-derive it
     * from {@code companies().isEmpty()} themselves.
     */
    public CareerHistoryAvailability careerHistoryAvailability() {
        return careerHistory
                .map(history -> history.companies().isEmpty()
                        ? CareerHistoryAvailability.EMPTY
                        : CareerHistoryAvailability.AVAILABLE)
                .orElse(CareerHistoryAvailability.NOT_PROVIDED);
    }
}

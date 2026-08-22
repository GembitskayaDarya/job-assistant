package com.darya.jobassistant.integrations.ai.openai;

import com.darya.jobassistant.candidatecontext.cv.model.CvSourceSnapshot;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sprint 11 Final CV Policy: prompt-local typed-reference layer for {@link
 * SpringAiCvTailoringAdapter} - the AI is never shown, and never asked to return, a raw domain
 * UUID. Every candidate skill is assigned a short, deterministic reference token ({@code SKILL_001},
 * {@code SKILL_002}, ...) for the lifetime of one tailoring request only; these references are never
 * persisted and carry no domain identity of their own.
 *
 * <p>A single flat namespace, unlike the earlier (pre Sprint 11 Final CV Policy) hierarchical
 * design this class used to have: since the AI CV-tailoring contract now answers exactly one
 * question - which skills, in what order - there is no parent/child ownership relationship left to
 * represent here at all. See {@code CoverLetterEvidenceReferenceIndex} for the same flat-namespace
 * shape applied to cover-letter evidence citation, for the identical reason.
 *
 * <p>{@link #build} walks {@code snapshot.candidateProfile().skills()} once, in existing display
 * order. The resulting index is used twice: once by {@link SpringAiCvTailoringAdapter} to render the
 * reference-labeled skill list ({@link #refOf}), and once - after the AI responds - to strictly
 * resolve every reference it returns back to the real candidate skill UUID ({@link #resolveRef}).
 * Resolution never guesses, substitutes by name, or silently drops an unresolvable reference: an
 * unknown reference throws {@link CvTailoringReferenceResolutionException}, caught by {@link
 * SpringAiCvTailoringAdapter#tailorSkills} as a malformed AI response, never a provider failure.
 */
final class CvTailoringReferenceIndex {

    private static final String PREFIX = "SKILL_";
    private static final String LABEL = "SKILL";

    private final Map<String, UUID> refToId = new LinkedHashMap<>();
    private final Map<UUID, String> idToRef = new HashMap<>();
    private int counter = 0;

    private CvTailoringReferenceIndex() {
    }

    static CvTailoringReferenceIndex build(CvSourceSnapshot snapshot) {
        CvTailoringReferenceIndex index = new CvTailoringReferenceIndex();
        for (CandidateSkillFacts skill : snapshot.candidateProfile().skills()) {
            if (skill.candidateSkillId() != null) {
                index.assign(skill.candidateSkillId());
            }
        }
        return index;
    }

    private void assign(UUID id) {
        String ref = PREFIX + String.format("%03d", ++counter);
        refToId.put(ref, id);
        idToRef.put(id, ref);
    }

    /** Forward lookup (UUID -> ref), used to render the prompt. */
    String refOf(UUID id) {
        return idToRef.get(id);
    }

    /** Strict resolution (ref -> UUID), used to map the AI's response. */
    UUID resolveRef(String ref) {
        if (ref == null || ref.isBlank()) {
            throw new CvTailoringReferenceResolutionException("AI returned a blank or missing " + LABEL + " reference", ref, LABEL, null);
        }
        UUID id = refToId.get(ref);
        if (id == null) {
            throw new CvTailoringReferenceResolutionException("AI returned an unknown " + LABEL + " reference '" + ref + "'", ref, LABEL, null);
        }
        return id;
    }
}

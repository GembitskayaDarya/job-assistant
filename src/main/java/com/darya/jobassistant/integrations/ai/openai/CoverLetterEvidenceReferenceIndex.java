package com.darya.jobassistant.integrations.ai.openai;

import com.darya.jobassistant.applicationmaterials.generation.model.ApplicationMaterialsAiException;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextForApplicationMaterials;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.SelectedCareerCompany;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.SelectedCareerPosition;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.SelectedCareerProject;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sprint 11 Final Application Package Quality Hardening: prompt-local reference layer for {@link
 * SpringAiApplicationMaterialsAdapter}, mirroring {@code CvTailoringReferenceIndex}'s established
 * pattern for the exact same reason it exists - the model is never shown, and never asked to return,
 * a real database id.
 *
 * <p>Root cause this replaces: the cover-letter prompt used to label every selectable evidence item
 * with its raw UUID directly inside the surrounding prose the model reads and paraphrases (e.g.
 * {@code "Achievement (id: 3fa85f64-...): Optimized performance..."}). A real production generation
 * showed the model occasionally echoing that same "(id: ...)" citation style back into its own
 * generated paragraph text - producing a visible {@code "(sourceIds: [uuid, uuid, ...])"} suffix in
 * user-facing prose - even though the response's separate, correct {@code sourceRefs} field was also
 * present (and often left empty). A flat namespace (unlike {@code CvTailoringReferenceIndex}'s several
 * owner-scoped namespaces) is sufficient here: this adapter has no nested "child must belong to this
 * exact parent" structural requirement - every selected evidence node (company, position, project,
 * responsibility, achievement, technology) is independently referenceable, so one incrementing token
 * per node is enough.
 */
final class CoverLetterEvidenceReferenceIndex {

    private static final String PREFIX = "EVIDENCE_";

    private final Map<String, UUID> refToId = new LinkedHashMap<>();
    private final Map<UUID, String> idToRef = new HashMap<>();
    private int counter = 0;

    private CoverLetterEvidenceReferenceIndex() {
    }

    static CoverLetterEvidenceReferenceIndex build(CandidateContextForApplicationMaterials context) {
        CoverLetterEvidenceReferenceIndex index = new CoverLetterEvidenceReferenceIndex();
        for (SelectedCareerCompany company : context.selectedCompanies()) {
            index.assign(company.careerCompanyId());
            for (SelectedCareerPosition position : company.positions()) {
                index.assign(position.careerPositionId());
                position.responsibilities().forEach(r -> index.assign(r.careerResponsibilityId()));
                position.achievements().forEach(a -> index.assign(a.careerAchievementId()));
                for (SelectedCareerProject project : position.projects()) {
                    index.assign(project.careerProjectId());
                    project.responsibilities().forEach(r -> index.assign(r.careerResponsibilityId()));
                    project.achievements().forEach(a -> index.assign(a.careerAchievementId()));
                    project.technologies().forEach(t -> index.assign(t.careerTechnologyId()));
                }
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

    /**
     * Strict resolution (ref -> UUID), used to map the AI's response. An unknown or blank reference
     * is a structural failure - thrown directly as {@link ApplicationMaterialsAiException} with no
     * cause, matching this adapter's existing {@code parseUuid} convention (cause-presence is the
     * signal {@code GenerateApplicationMaterialsUseCase} uses to distinguish a malformed response from
     * a provider failure - see that class's javadoc).
     */
    UUID resolveRef(String ref) {
        if (ref == null || ref.isBlank()) {
            throw new ApplicationMaterialsAiException("AI provider returned a blank or missing evidence reference");
        }
        UUID id = refToId.get(ref);
        if (id == null) {
            throw new ApplicationMaterialsAiException("AI provider returned an unknown evidence reference '" + ref + "'");
        }
        return id;
    }
}

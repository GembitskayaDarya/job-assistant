package com.darya.jobassistant.applicationmaterials.render.model;

import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedApplicationMaterials;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetter;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetterParagraph;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCv;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCvExperience;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedExperienceBullet;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextForApplicationMaterials;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.SelectedCareerCompany;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.SelectedCareerPosition;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sprint 10 Step 4: deterministic, AI-free, database-free assembly of the immutable {@link
 * RenderableApplicationMaterials} render snapshot from a generation's already-validated {@link
 * GeneratedApplicationMaterials} semantic result and the exact {@link
 * CandidateContextForApplicationMaterials} that generation used.
 *
 * <h2>Why this class exists</h2>
 *
 * {@link GeneratedCvExperience} deliberately carries only a {@code careerPositionId} - no company
 * name, position title, or employment dates, so the AI structurally cannot supply or alter those
 * facts (see that record's javadoc). This class is where those canonical facts are actually
 * resolved: for every experience, {@link #resolveExperience} looks the referenced position up in
 * {@code context} and copies its real company name, title, dates, location, and selected projects
 * verbatim. The result - not the bare id - is what gets persisted as the render snapshot and handed
 * to the PDF renderer, so canonical metadata is fixed at snapshot-creation time and is never
 * re-derived from (or vulnerable to drifting alongside) the AI response again.
 *
 * <p>Called exactly once per generation, only when no {@code ApplicationMaterialRenderSnapshot}
 * exists yet - see {@code RenderApplicationMaterialsUseCase}.
 */
public final class RenderModelAssembler {

    private RenderModelAssembler() {
    }

    public static RenderableApplicationMaterials assemble(
            GeneratedApplicationMaterials semanticResult, CandidateContextForApplicationMaterials context, JobOffer vacancy) {
        if (semanticResult == null) {
            throw new IllegalArgumentException("Render model assembler semanticResult must not be null");
        }
        if (context == null) {
            throw new IllegalArgumentException("Render model assembler context must not be null");
        }
        if (vacancy == null) {
            throw new IllegalArgumentException("Render model assembler vacancy must not be null");
        }
        return new RenderableApplicationMaterials(
                assembleCv(semanticResult.cv(), context), assembleCoverLetter(semanticResult.coverLetter(), vacancy));
    }

    private static RenderableCv assembleCv(GeneratedCv generated, CandidateContextForApplicationMaterials context) {
        PositionIndex index = PositionIndex.build(context);
        List<RenderableCvExperience> experiences = generated.experiences().stream()
                .map(experience -> resolveExperience(experience, index))
                .toList();
        return new RenderableCv(
                context.candidateProfile().targetRole(),
                context.candidateProfile().targetSeniority(),
                context.candidateProfile().experienceYears(),
                generated.headline(),
                generated.professionalSummary(),
                generated.skills(),
                experiences,
                generated.languages());
    }

    /**
     * @throws IllegalStateException if {@code experience} references a {@code careerPositionId}
     *     absent from {@code index} - this should never happen for a semantic result that already
     *     passed {@code GeneratedApplicationMaterialsValidator} against this exact context; a
     *     defensive guard against a caller assembling with the wrong context, not a normal
     *     user-facing failure.
     */
    private static RenderableCvExperience resolveExperience(GeneratedCvExperience experience, PositionIndex index) {
        ResolvedPosition resolved = index.byPositionId().get(experience.careerPositionId());
        if (resolved == null) {
            throw new IllegalStateException(
                    "Semantic result references careerPositionId '" + experience.careerPositionId()
                            + "' that is not present in the supplied candidate context - "
                            + "the context passed to RenderModelAssembler must be the exact one this generation was validated against");
        }
        SelectedCareerPosition position = resolved.position();
        List<String> bullets = experience.bullets().stream().map(GeneratedExperienceBullet::text).toList();
        List<RenderableCvProject> projects = position.projects().stream()
                .map(project -> new RenderableCvProject(project.name(), project.startDate(), project.endDate()))
                .toList();
        return new RenderableCvExperience(
                resolved.companyName(), position.title(), position.employmentType(), position.location(),
                position.workArrangement(), position.startDate(), position.endDate(), position.currentRole(),
                bullets, projects);
    }

    private static RenderableCoverLetter assembleCoverLetter(GeneratedCoverLetter generated, JobOffer vacancy) {
        List<String> paragraphs = generated.paragraphs().stream().map(GeneratedCoverLetterParagraph::text).toList();
        return new RenderableCoverLetter(generated.greeting(), paragraphs, generated.closing(), vacancy.title(), vacancy.company());
    }

    private record ResolvedPosition(String companyName, SelectedCareerPosition position) {
    }

    /** Flattens every selected company's positions into one id-keyed lookup, built once per {@link #assemble} call. */
    private record PositionIndex(Map<UUID, ResolvedPosition> byPositionId) {

        static PositionIndex build(CandidateContextForApplicationMaterials context) {
            Map<UUID, ResolvedPosition> map = new HashMap<>();
            for (SelectedCareerCompany company : context.selectedCompanies()) {
                for (SelectedCareerPosition position : company.positions()) {
                    map.put(position.careerPositionId(), new ResolvedPosition(company.name(), position));
                }
            }
            return new PositionIndex(map);
        }
    }
}

package com.darya.jobassistant.applicationmaterials.render.model;

import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetter;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetterParagraph;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.util.List;

/**
 * Sprint 10 Step 4 (Sprint 11 Big Block 7 correction): deterministic, AI-free, database-free
 * assembly of the cover-letter half of a {@link RenderableApplicationMaterials} render snapshot from
 * a generation's already-validated {@link GeneratedCoverLetter} and the target {@link JobOffer}.
 *
 * <p>The CV half no longer needs assembly here: {@code TailoredCvDocument} (Sprint 11's {@code
 * CvAssembler} output) is already the complete, canonical, presentation-ready CV content - callers
 * build {@link RenderableApplicationMaterials} directly from it, with no intermediate resolution
 * step. This class's only remaining job is the cover letter, which still goes through the Sprint 10
 * AI path (see {@code ApplicationMaterialsAiPort}).
 */
public final class RenderModelAssembler {

    private RenderModelAssembler() {
    }

    public static RenderableCoverLetter assembleCoverLetter(GeneratedCoverLetter generated, JobOffer vacancy) {
        if (generated == null) {
            throw new IllegalArgumentException("Render model assembler generated cover letter must not be null");
        }
        if (vacancy == null) {
            throw new IllegalArgumentException("Render model assembler vacancy must not be null");
        }
        List<String> paragraphs = generated.paragraphs().stream().map(GeneratedCoverLetterParagraph::text).toList();
        return new RenderableCoverLetter(generated.greeting(), paragraphs, generated.closing(), vacancy.title(), vacancy.company());
    }
}

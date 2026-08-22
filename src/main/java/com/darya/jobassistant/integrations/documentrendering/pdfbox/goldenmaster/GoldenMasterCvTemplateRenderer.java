package com.darya.jobassistant.integrations.documentrendering.pdfbox.goldenmaster;

import com.darya.jobassistant.applicationmaterials.render.model.DocumentRenderingException;
import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

/**
 * Sprint 11 Golden Master Template Rendering: the public entry point for this package -
 * {@code PdfBoxApplicationMaterialDocumentRenderer} calls only this, never {@link
 * TechnicalSkillsRegionLocator}/{@link GoldenMasterCvRenderer} directly. Loads a fresh mutable copy
 * of the golden master template from {@link GoldenMasterCvTemplate} and performs the Technical
 * Skills replacement (see {@link GoldenMasterCvRenderer}'s javadoc) for exactly one render call.
 */
@Component
public class GoldenMasterCvTemplateRenderer {

    private final GoldenMasterCvTemplate template;

    public GoldenMasterCvTemplateRenderer(GoldenMasterCvTemplate template) {
        this.template = template;
    }

    public record Result(byte[] pdfBytes, List<String> renderedSkills) {
    }

    public Result render(List<String> skills) {
        try (PDDocument document = template.freshDocument()) {
            GoldenMasterCvRenderer.Result result = GoldenMasterCvRenderer.render(document, skills);
            return new Result(result.pdfBytes(), result.renderedSkills());
        } catch (IOException | GoldenMasterCvTemplateException e) {
            throw new DocumentRenderingException("Failed to render CV from the golden master template", e);
        }
    }
}

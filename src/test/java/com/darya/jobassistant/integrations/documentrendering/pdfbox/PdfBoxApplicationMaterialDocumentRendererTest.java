package com.darya.jobassistant.integrations.documentrendering.pdfbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.darya.jobassistant.applicationmaterials.render.model.RenderableCoverLetter;
import com.darya.jobassistant.applicationmaterials.render.model.RenderedDocument;
import com.darya.jobassistant.integrations.documentrendering.pdfbox.goldenmaster.GoldenMasterCvTemplateRenderer;
import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/**
 * Sprint 11 Golden Master Template Rendering: {@link PdfBoxApplicationMaterialDocumentRenderer#renderCv}
 * now delegates entirely to {@link GoldenMasterCvTemplateRenderer} (see that package's own test suite
 * for CV coverage - genuine content-stream text removal, display-fit skill truncation, template
 * integrity) - this class keeps only the still-applicable cover-letter coverage, unaffected by that
 * change. {@link #renderer} is given a mock {@link GoldenMasterCvTemplateRenderer} since no test here
 * ever calls {@code renderCv}.
 */
class PdfBoxApplicationMaterialDocumentRendererTest {

    private final PdfBoxApplicationMaterialDocumentRenderer renderer =
            new PdfBoxApplicationMaterialDocumentRenderer(mock(GoldenMasterCvTemplateRenderer.class));

    @Test
    void renderCoverLetter_producesValidPdfWithExtractableText() throws IOException {
        RenderableCoverLetter coverLetter = new RenderableCoverLetter(
                "Dear Hiring Manager,",
                List.of("I am excited to apply for this role.", "My experience aligns well with your needs."),
                "Sincerely, the candidate",
                "Senior Backend Engineer", "Acme Corp");

        RenderedDocument document = renderer.renderCoverLetter(coverLetter);

        assertThat(document.contentType()).isEqualTo("application/pdf");
        String text = extractText(document.content());
        assertThat(text).contains("Re: Senior Backend Engineer at Acme Corp");
        assertThat(text).contains("Dear Hiring Manager");
        assertThat(text).contains("I am excited to apply");
        assertThat(text).contains("Sincerely, the candidate");
    }

    @Test
    void renderCoverLetter_sameInput_producesByteIdenticalOutput() {
        RenderableCoverLetter coverLetter = new RenderableCoverLetter(
                null, List.of("Paragraph one.", "Paragraph two."), "Regards", "Engineer", "Acme");

        RenderedDocument first = renderer.renderCoverLetter(coverLetter);
        RenderedDocument second = renderer.renderCoverLetter(coverLetter);

        assertThat(first.content()).isEqualTo(second.content());
    }

    @Test
    void renderCoverLetter_polishAndCyrillicText_isPreservedExactly() throws IOException {
        RenderableCoverLetter coverLetter = new RenderableCoverLetter(
                "Szanowni Państwo,",
                List.of("Zażółć gęślą jaźń.", "Готов приступить к работе немедленно."),
                "Z poważaniem",
                "Starszy Inżynier", "Łódzka Firma");

        RenderedDocument document = renderer.renderCoverLetter(coverLetter);

        String text = extractText(document.content());
        assertThat(text).contains("Szanowni Państwo");
        assertThat(text).contains("Zażółć gęślą jaźń");
        assertThat(text).contains("Готов приступить к работе немедленно");
        assertThat(text).contains("Z poważaniem");
        assertThat(text).contains("Łódzka Firma");
    }

    private String extractText(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return new PDFTextStripper().getText(document);
        }
    }
}

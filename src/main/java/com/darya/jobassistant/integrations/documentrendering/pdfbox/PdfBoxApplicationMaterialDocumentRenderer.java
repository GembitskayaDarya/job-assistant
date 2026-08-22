package com.darya.jobassistant.integrations.documentrendering.pdfbox;

import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialDocumentRendererPort;
import com.darya.jobassistant.applicationmaterials.render.model.DocumentRenderingException;
import com.darya.jobassistant.applicationmaterials.render.model.RenderableCoverLetter;
import com.darya.jobassistant.applicationmaterials.render.model.RenderedDocument;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvDocument;
import com.darya.jobassistant.integrations.documentrendering.pdfbox.goldenmaster.GoldenMasterCvTemplateRenderer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

/**
 * Sprint 10 Step 4 (Sprint 11 Golden Master Template Rendering rewrite): implements {@link
 * ApplicationMaterialDocumentRendererPort}.
 *
 * <h2>CV rendering (Sprint 11 Golden Master Template Rendering)</h2>
 *
 * {@link #renderCv} no longer draws a CV from scratch. Every earlier attempt to manually reproduce
 * the approved reference CV's exact typography/spacing/rules in PDFBox - however carefully measured
 * off the golden master - still visibly diverged from it once compared side by side, because it was
 * always a from-scratch <em>reproduction</em>, never the golden master itself. The golden master PDF
 * ({@code config/private/cv/golden-master/Darya_Hembitskaya_CV.pdf}) <strong>is</strong> the approved
 * CV; {@link #renderCv} now delegates entirely to {@link GoldenMasterCvTemplateRenderer}, which loads
 * that exact file and replaces only its Technical Skills value line - the one section that actually
 * varies per vacancy (see {@link TailoredCvDocument#withSkills}, the domain's own enforcement that
 * Technical Skills is the only vacancy-dependent transformation a fully-assembled CV may ever
 * undergo). Every other section - header, Professional Summary, Professional Experience (including
 * Netcracker's forced page-2 break), Mentoring, Personal Project, Education, Languages, rules,
 * margins, page 2 in full - is the golden master's own untouched content, never redrawn, so it is
 * byte-for-byte the approved template rather than an approximation of it. See that package's javadoc
 * for exactly how the old value is genuinely removed from (not merely painted over in) the PDF
 * content stream, and how the new value is inserted.
 *
 * <h2>Cover letter rendering</h2>
 *
 * {@link #renderCoverLetter} is unaffected by the above - free-form prose has no fixed approved
 * template to reuse, so it is still drawn directly with PDFBox via {@link PdfPageCursor}, exactly as
 * before.
 *
 * <h2>Fonts (cover letter only)</h2>
 *
 * Uses {@link PDType0Font} loaded from static (non-variable) Inter TTF files vendored directly under
 * {@code src/main/resources/fonts/ttf/Inter/} - Regular and Italic only, the two faces {@link
 * #writeCoverLetter} actually uses. See {@code src/main/resources/fonts/ttf/Inter/README.txt} for
 * exactly where these files came from (the official rsms/inter GitHub release v4.1's {@code
 * extras/ttf/} static build) and their SIL Open Font License 1.1. Vendored at build time, never
 * fetched over the network at runtime and never read from the local OS. {@link #loadFonts} fails
 * loudly ({@link IOException}) if either bundled file is missing from the classpath - never a silent
 * fallback to a different, unintended font.
 *
 * <h2>Determinism</h2>
 *
 * {@link PDDocument#setDocumentId} is pinned to a fixed constant before saving - left to PDFBox's
 * default, the trailer {@code /ID} would otherwise be regenerated (effectively randomly) on every
 * save, making two renders of the exact same {@link RenderableCoverLetter} produce different bytes
 * and defeating {@code FileStoragePort}'s same-content idempotency check. No document-info
 * creation/modification date is set, so none is emitted either. A new {@link PDDocument} (and
 * therefore a fresh {@link PDType0Font} load) is created per render call, never shared or cached
 * across calls. {@link GoldenMasterCvTemplateRenderer}'s CV path has its own, separate determinism
 * guarantee - see that package's javadoc.
 *
 * <h2>Boundary</h2>
 *
 * This class only converts an already-assembled, trusted render model into bytes - see {@link
 * ApplicationMaterialDocumentRendererPort}'s javadoc for the full list of things it must never do,
 * enforced by an architecture test.
 */
@Component
public class PdfBoxApplicationMaterialDocumentRenderer implements ApplicationMaterialDocumentRendererPort {

    /** A4, not US Letter - this project's candidate profile and target market are Europe-oriented (see CandidateProfile/CLAUDE.md). Cover letter only - the CV path reuses the golden master template's own page size. */
    private static final PDRectangle PAGE_SIZE = PDRectangle.A4;

    private static final float MARGIN = 51.02f;
    private static final float FONT_SIZE_BODY = 10.5f;
    private static final float LEADING_BODY = 13f;
    private static final float SECTION_GAP = 16f;
    private static final float ROLE_GAP = 8f;

    /** Fixed so every render of identical content produces byte-identical output - see class javadoc. */
    private static final long FIXED_DOCUMENT_ID = 1L;

    private static final String FONT_RESOURCE_REGULAR = "fonts/ttf/Inter/Inter-Regular.ttf";
    private static final String FONT_RESOURCE_ITALIC = "fonts/ttf/Inter/Inter-Italic.ttf";

    private final GoldenMasterCvTemplateRenderer goldenMasterCvTemplateRenderer;

    public PdfBoxApplicationMaterialDocumentRenderer(GoldenMasterCvTemplateRenderer goldenMasterCvTemplateRenderer) {
        this.goldenMasterCvTemplateRenderer = goldenMasterCvTemplateRenderer;
    }

    /** The two faces {@link #writeCoverLetter} uses - {@link PDType0Font} is bound to one {@link PDDocument}, so this cannot be static. */
    private record Fonts(PDType0Font regular, PDType0Font italic) {
    }

    @Override
    public RenderedDocument renderCv(TailoredCvDocument cv) {
        GoldenMasterCvTemplateRenderer.Result result = goldenMasterCvTemplateRenderer.render(cv.skills());
        return new RenderedDocument(result.pdfBytes(), "application/pdf", result.renderedSkills());
    }

    @Override
    public RenderedDocument renderCoverLetter(RenderableCoverLetter coverLetter) {
        try (PDDocument document = new PDDocument()) {
            document.setDocumentId(FIXED_DOCUMENT_ID);
            Fonts fonts = loadFonts(document);
            try (PdfPageCursor cursor = new PdfPageCursor(document, PAGE_SIZE, MARGIN, MARGIN, MARGIN, MARGIN)) {
                writeCoverLetter(cursor, fonts, coverLetter);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return new RenderedDocument(out.toByteArray(), "application/pdf");
        } catch (IOException | RuntimeException e) {
            throw new DocumentRenderingException("Failed to render application material PDF", e);
        }
    }

    private Fonts loadFonts(PDDocument document) throws IOException {
        return new Fonts(loadFont(document, FONT_RESOURCE_REGULAR), loadFont(document, FONT_RESOURCE_ITALIC));
    }

    /** {@code embedSubset=true}: only the glyphs actually used are embedded - deterministic, see class javadoc. */
    private PDType0Font loadFont(PDDocument document, String classpathResource) throws IOException {
        try (InputStream fontStream = getClass().getClassLoader().getResourceAsStream(classpathResource)) {
            if (fontStream == null) {
                throw new IOException("Bundled font resource not found on classpath: " + classpathResource);
            }
            return PDType0Font.load(document, fontStream, true);
        }
    }

    // ==================== Cover letter ====================

    private void writeCoverLetter(PdfPageCursor cursor, Fonts fonts, RenderableCoverLetter coverLetter) throws IOException {
        if (coverLetter.vacancyTitle() != null || coverLetter.vacancyCompany() != null) {
            cursor.writeWrapped(formatVacancyReference(coverLetter), fonts.italic(), FONT_SIZE_BODY, LEADING_BODY, 0);
            cursor.addSpacing(SECTION_GAP);
        }
        if (coverLetter.greeting() != null && !coverLetter.greeting().isBlank()) {
            cursor.writeWrapped(coverLetter.greeting(), fonts.regular(), FONT_SIZE_BODY, LEADING_BODY, 0);
            cursor.addSpacing(ROLE_GAP);
        }
        for (String paragraph : coverLetter.paragraphs()) {
            cursor.writeWrapped(paragraph, fonts.regular(), FONT_SIZE_BODY, LEADING_BODY, 0);
            cursor.addSpacing(ROLE_GAP);
        }
        cursor.addSpacing(SECTION_GAP - ROLE_GAP);
        cursor.writeWrapped(coverLetter.closing(), fonts.regular(), FONT_SIZE_BODY, LEADING_BODY, 0);
    }

    private String formatVacancyReference(RenderableCoverLetter coverLetter) {
        StringBuilder reference = new StringBuilder("Re: ");
        reference.append(coverLetter.vacancyTitle() == null ? "the open position" : coverLetter.vacancyTitle());
        if (coverLetter.vacancyCompany() != null && !coverLetter.vacancyCompany().isBlank()) {
            reference.append(" at ").append(coverLetter.vacancyCompany());
        }
        return reference.toString();
    }
}

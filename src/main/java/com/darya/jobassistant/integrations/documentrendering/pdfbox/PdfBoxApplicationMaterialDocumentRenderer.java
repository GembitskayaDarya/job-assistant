package com.darya.jobassistant.integrations.documentrendering.pdfbox;

import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCvSkill;
import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialDocumentRendererPort;
import com.darya.jobassistant.applicationmaterials.render.model.DocumentRenderingException;
import com.darya.jobassistant.applicationmaterials.render.model.RenderableCoverLetter;
import com.darya.jobassistant.applicationmaterials.render.model.RenderableCv;
import com.darya.jobassistant.applicationmaterials.render.model.RenderableCvExperience;
import com.darya.jobassistant.applicationmaterials.render.model.RenderableCvProject;
import com.darya.jobassistant.applicationmaterials.render.model.RenderedDocument;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

/**
 * Sprint 10 Step 4: the only layer that knows CV/cover-letter PDFs are currently produced with
 * Apache PDFBox - implements {@link ApplicationMaterialDocumentRendererPort}. Chosen over
 * alternatives considered for this step: a browser/Chromium-based HTML-to-PDF renderer was
 * explicitly out of scope (heavy runtime, unnecessary here); an HTML/CSS-to-PDF library (e.g.
 * openhtmltopdf) would have required its own escaping discipline for dynamic text and a templating
 * layer neither of which this step otherwise needs. PDFBox draws vector text directly onto measured
 * positions ({@link PdfPageCursor}) - simpler, no markup/escaping surface exists to get wrong, plain
 * Apache-2.0 Java with no native/Chromium dependency.
 *
 * <h2>Fonts</h2>
 *
 * Uses {@link PDType0Font} loaded from the Open Sans TTF family bundled on the classpath by the
 * {@code com.helger.font:ph-fonts-open-sans} Maven Central artifact (Apache-2.0 wrapper around
 * SIL Open Font License 1.1 font files, redistribution-permitted) - never a font read from the
 * local OS and never fetched over the network at runtime, so output uses the exact same glyph
 * data on a developer machine and inside the Docker container regardless of what fonts either
 * happens to have installed. Open Sans covers Latin, Latin Extended (including Polish diacritics)
 * and Cyrillic; text stays vector/selectable/searchable - never rasterized. Each font is embedded
 * with a subset ({@code embedSubset=true}) containing only the glyphs actually used, keeping file
 * size down; see the class-level determinism note below for why this remains safe. Characters
 * genuinely outside the embedded font's coverage (or control characters) are handled by {@link
 * PdfTextSanitizer} rather than transliterated.
 *
 * <h2>Determinism</h2>
 *
 * {@link PDDocument#setDocumentId} is pinned to a fixed constant before saving - left to PDFBox's
 * default, the trailer {@code /ID} would otherwise be regenerated (effectively randomly) on every
 * save, making two renders of the exact same {@link RenderableCv}/{@link RenderableCoverLetter}
 * produce different bytes and defeating {@code FileStoragePort}'s same-content idempotency check.
 * No document-info creation/modification date is set, so none is emitted either. PDFBox's
 * TrueType subset-tag generation ({@code TrueTypeEmbedder}) is itself deterministic - a hash of
 * the subsetted glyph set, not random - so embedding a font subset per render does not break
 * byte-identical output for identical input; a new {@link PDDocument} (and therefore a fresh
 * {@link PDType0Font} load) is created per render call, never shared or cached across calls.
 *
 * <h2>Boundary</h2>
 *
 * This class only converts an already-assembled, trusted render model into bytes - see {@link
 * ApplicationMaterialDocumentRendererPort}'s javadoc for the full list of things it must never do,
 * enforced by an architecture test.
 */
@Component
public class PdfBoxApplicationMaterialDocumentRenderer implements ApplicationMaterialDocumentRendererPort {

    /** A4, not US Letter - this project's candidate profile and target market are Europe-oriented (see CandidateProfile/CLAUDE.md). */
    private static final PDRectangle PAGE_SIZE = PDRectangle.A4;
    private static final float MARGIN = 50f;
    private static final float FONT_SIZE_TITLE = 16f;
    private static final float FONT_SIZE_HEADING = 12f;
    private static final float FONT_SIZE_BODY = 10f;
    private static final float LEADING_TITLE = 20f;
    private static final float LEADING_HEADING = 16f;
    private static final float LEADING_BODY = 13f;
    private static final float SECTION_SPACING = 8f;
    private static final float ITEM_SPACING = 4f;
    private static final float BULLET_INDENT = 12f;

    /** Fixed so every render of identical content produces byte-identical output - see class javadoc. */
    private static final long FIXED_DOCUMENT_ID = 1L;

    private static final String FONT_RESOURCE_REGULAR = "fonts/ttf/OpenSans/OpenSans-Regular.ttf";
    private static final String FONT_RESOURCE_BOLD = "fonts/ttf/OpenSans/OpenSans-Bold.ttf";
    private static final String FONT_RESOURCE_ITALIC = "fonts/ttf/OpenSans/OpenSans-Italic.ttf";

    /** The three faces loaded for a single render call - {@link PDType0Font} is bound to one {@link PDDocument}, so this cannot be static. */
    private record Fonts(PDType0Font regular, PDType0Font bold, PDType0Font italic) {
    }

    @Override
    public RenderedDocument renderCv(RenderableCv cv) {
        return render((cursor, fonts) -> writeCv(cursor, fonts, cv));
    }

    @Override
    public RenderedDocument renderCoverLetter(RenderableCoverLetter coverLetter) {
        return render((cursor, fonts) -> writeCoverLetter(cursor, fonts, coverLetter));
    }

    private interface CursorWriter {
        void write(PdfPageCursor cursor, Fonts fonts) throws IOException;
    }

    private RenderedDocument render(CursorWriter writer) {
        try (PDDocument document = new PDDocument()) {
            document.setDocumentId(FIXED_DOCUMENT_ID);
            Fonts fonts = loadFonts(document);
            try (PdfPageCursor cursor = new PdfPageCursor(document, PAGE_SIZE, MARGIN, MARGIN, MARGIN, MARGIN)) {
                writer.write(cursor, fonts);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return new RenderedDocument(out.toByteArray(), "application/pdf");
        } catch (IOException | RuntimeException e) {
            throw new DocumentRenderingException("Failed to render application material PDF", e);
        }
    }

    private Fonts loadFonts(PDDocument document) throws IOException {
        return new Fonts(
                loadFont(document, FONT_RESOURCE_REGULAR),
                loadFont(document, FONT_RESOURCE_BOLD),
                loadFont(document, FONT_RESOURCE_ITALIC));
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

    // ==================== CV ====================

    private void writeCv(PdfPageCursor cursor, Fonts fonts, RenderableCv cv) throws IOException {
        cursor.writeLine(cv.targetRole(), fonts.bold(), FONT_SIZE_TITLE, LEADING_TITLE, 0);
        String subtitle = cv.seniority() + " - " + cv.totalExperienceYears() + " years of experience";
        cursor.writeLine(subtitle, fonts.italic(), FONT_SIZE_BODY, LEADING_BODY, 0);
        cursor.addSpacing(SECTION_SPACING);

        cursor.writeWrapped(cv.headline(), fonts.bold(), FONT_SIZE_HEADING, LEADING_HEADING, 0);
        cursor.addSpacing(ITEM_SPACING);
        cursor.writeWrapped(cv.professionalSummary(), fonts.regular(), FONT_SIZE_BODY, LEADING_BODY, 0);
        cursor.addSpacing(SECTION_SPACING);

        if (!cv.skills().isEmpty()) {
            writeSectionHeading(cursor, fonts, "Skills");
            cursor.writeWrapped(formatSkills(cv.skills()), fonts.regular(), FONT_SIZE_BODY, LEADING_BODY, 0);
            cursor.addSpacing(SECTION_SPACING);
        }

        if (!cv.experiences().isEmpty()) {
            writeSectionHeading(cursor, fonts, "Experience");
            for (RenderableCvExperience experience : cv.experiences()) {
                writeExperience(cursor, fonts, experience);
                cursor.addSpacing(ITEM_SPACING);
            }
            cursor.addSpacing(SECTION_SPACING - ITEM_SPACING);
        }

        if (!cv.languages().isEmpty()) {
            writeSectionHeading(cursor, fonts, "Languages");
            cursor.writeWrapped(String.join(", ", cv.languages()), fonts.regular(), FONT_SIZE_BODY, LEADING_BODY, 0);
        }
    }

    private void writeExperience(PdfPageCursor cursor, Fonts fonts, RenderableCvExperience experience) throws IOException {
        cursor.writeWrapped(experience.positionTitle() + " - " + experience.companyName(), fonts.bold(), FONT_SIZE_BODY, LEADING_BODY, 0);
        String meta = formatExperienceMeta(experience);
        if (!meta.isBlank()) {
            cursor.writeWrapped(meta, fonts.italic(), FONT_SIZE_BODY, LEADING_BODY, 0);
        }
        for (String bullet : experience.bullets()) {
            cursor.writeWrapped("- " + bullet, fonts.regular(), FONT_SIZE_BODY, LEADING_BODY, BULLET_INDENT);
        }
        for (RenderableCvProject project : experience.projects()) {
            cursor.writeWrapped("Project: " + project.name() + formatProjectDateRange(project), fonts.italic(), FONT_SIZE_BODY, LEADING_BODY, BULLET_INDENT);
        }
    }

    private String formatExperienceMeta(RenderableCvExperience experience) {
        StringBuilder meta = new StringBuilder();
        appendIfPresent(meta, experience.employmentType());
        appendIfPresent(meta, experience.workArrangement());
        appendIfPresent(meta, experience.location());
        String dateRange = formatDateRange(experience.startDate(), experience.endDate(), experience.currentRole());
        if (!dateRange.isBlank()) {
            if (!meta.isEmpty()) {
                meta.append(" | ");
            }
            meta.append(dateRange);
        }
        return meta.toString();
    }

    private void appendIfPresent(StringBuilder builder, String value) {
        if (value != null && !value.isBlank()) {
            if (!builder.isEmpty()) {
                builder.append(" | ");
            }
            builder.append(value);
        }
    }

    private String formatSkills(List<GeneratedCvSkill> skills) {
        return skills.stream()
                .map(skill -> skill.proficiency() == null ? skill.name() : skill.name() + " (" + skill.proficiency() + ")")
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private String formatDateRange(LocalDate start, LocalDate end, boolean currentRole) {
        if (start == null && end == null && !currentRole) {
            return "";
        }
        String startText = start == null ? "unknown" : start.toString();
        String endText = currentRole ? "present" : (end == null ? "unknown" : end.toString());
        return startText + " - " + endText;
    }

    private String formatProjectDateRange(RenderableCvProject project) {
        String range = formatDateRange(project.startDate(), project.endDate(), false);
        return range.isBlank() ? "" : " (" + range + ")";
    }

    // ==================== Cover letter ====================

    private void writeCoverLetter(PdfPageCursor cursor, Fonts fonts, RenderableCoverLetter coverLetter) throws IOException {
        if (coverLetter.vacancyTitle() != null || coverLetter.vacancyCompany() != null) {
            cursor.writeWrapped(formatVacancyReference(coverLetter), fonts.italic(), FONT_SIZE_BODY, LEADING_BODY, 0);
            cursor.addSpacing(SECTION_SPACING);
        }
        if (coverLetter.greeting() != null && !coverLetter.greeting().isBlank()) {
            cursor.writeWrapped(coverLetter.greeting(), fonts.regular(), FONT_SIZE_BODY, LEADING_BODY, 0);
            cursor.addSpacing(ITEM_SPACING);
        }
        for (String paragraph : coverLetter.paragraphs()) {
            cursor.writeWrapped(paragraph, fonts.regular(), FONT_SIZE_BODY, LEADING_BODY, 0);
            cursor.addSpacing(ITEM_SPACING);
        }
        cursor.addSpacing(SECTION_SPACING - ITEM_SPACING);
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

    private void writeSectionHeading(PdfPageCursor cursor, Fonts fonts, String text) throws IOException {
        cursor.writeLine(text, fonts.bold(), FONT_SIZE_HEADING, LEADING_HEADING, 0);
    }
}

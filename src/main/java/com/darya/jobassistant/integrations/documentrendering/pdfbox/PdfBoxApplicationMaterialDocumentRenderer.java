package com.darya.jobassistant.integrations.documentrendering.pdfbox;

import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialDocumentRendererPort;
import com.darya.jobassistant.applicationmaterials.render.model.CvDateRangeFormatter;
import com.darya.jobassistant.applicationmaterials.render.model.CvSectionHeadings;
import com.darya.jobassistant.applicationmaterials.render.model.CvUrlDisplay;
import com.darya.jobassistant.applicationmaterials.render.model.DocumentRenderingException;
import com.darya.jobassistant.applicationmaterials.render.model.RenderableCoverLetter;
import com.darya.jobassistant.applicationmaterials.render.model.RenderedDocument;
import com.darya.jobassistant.candidates.CandidateEducationFacts;
import com.darya.jobassistant.candidates.CandidateLanguageFacts;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvCompany;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvDocument;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvHeader;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvPersonalProject;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvPosition;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvProject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

/**
 * Sprint 10 Step 4 (Sprint 11 Big Block 7 rewrite): the only layer that knows CV/cover-letter PDFs
 * are currently produced with Apache PDFBox - implements {@link ApplicationMaterialDocumentRendererPort}.
 * PDFBox draws vector text directly onto measured positions ({@link PdfPageCursor}) - simpler than an
 * HTML/CSS-to-PDF pipeline, no markup/escaping surface to get wrong, plain Apache-2.0 Java with no
 * native/Chromium dependency.
 *
 * <h2>Layout policy (Sprint 11 Big Block 7)</h2>
 *
 * A4, strictly single-column, ATS-first: every section is one linear top-to-bottom flow (no
 * multi-column experience, no side-by-side Responsibilities/Achievements or Education/Languages, no
 * tables). Roles render as plain linear text ({@code "Component Lead | Feb 2026 - Present"}), never
 * as separate visual columns. Contacts are ordinary body text, never icons. One font family
 * throughout (Open Sans) with regular/semibold/bold/italic weights only. This class contains no
 * candidate- or company-specific logic whatsoever - every string it draws comes from the {@link
 * TailoredCvDocument}/{@link RenderableCoverLetter} it is given; the approved Universal CV's exact
 * content is decided entirely above this class (see {@code GenerateBaselineCvUseCase}/{@code
 * BaselineCvSelectionResolver}), never hardcoded here.
 *
 * <h2>Order preservation (final acceptance correction)</h2>
 *
 * This class performs no ordering/sorting of its own whatsoever - {@link TailoredCvDocument#experience()}
 * and each company's {@code positions()} already carry their final, most-recent/current-first display
 * order (decided exactly once, by {@code CvAssembler} - see its "Canonical company/position display
 * order" javadoc section) by the time they reach this renderer. {@link #writeCv}/{@link #writeCompany}
 * iterate both lists exactly as given. A previous production defect had this class independently
 * re-sorting positions into recency order at render time while {@code AtsCvVerifier} verified the
 * assembler's (then still source-order) {@code TailoredCvDocument} - the renderer and verifier
 * disagreed about where each position's text actually landed on the page, and every real CV with more
 * than one position at the same company failed ATS verification. Never reintroduce sorting here.
 *
 * <h2>Fonts</h2>
 *
 * Uses {@link PDType0Font} loaded from the Open Sans TTF family bundled on the classpath by the
 * {@code com.helger.font:ph-fonts-open-sans} Maven Central artifact (Apache-2.0 wrapper around SIL
 * Open Font License 1.1 font files, redistribution-permitted) - never a font read from the local OS
 * and never fetched over the network at runtime, so output uses the exact same glyph data on a
 * developer machine and inside the Docker container regardless of what fonts either happens to have
 * installed. Open Sans covers Latin, Latin Extended (including Polish diacritics) and Cyrillic; text
 * stays vector/selectable/searchable - never rasterized. Regular/Bold/SemiBold/Italic faces are all
 * already present in that same bundled family (no new font dependency needed for the semibold
 * weight); each is embedded with a subset ({@code embedSubset=true}) containing only the glyphs
 * actually used. Characters genuinely outside the embedded font's coverage (or control characters)
 * are handled by {@link PdfTextSanitizer} rather than transliterated.
 *
 * <h2>Hyperlinks</h2>
 *
 * LinkedIn (header) and a Personal Project's URL are drawn via {@link PdfPageCursor#writeLineWithLink}
 * - visible, compact display text (scheme stripped, see {@link CvUrlDisplay#displayText}) with an
 * invisible-border {@code PDAnnotationLink}/{@code PDActionURI} pointing at the full, scheme-
 * qualified URL ({@link CvUrlDisplay#hyperlinkTarget}), so the link is genuinely clickable without a
 * distracting blue box.
 *
 * <h2>Determinism</h2>
 *
 * {@link PDDocument#setDocumentId} is pinned to a fixed constant before saving - left to PDFBox's
 * default, the trailer {@code /ID} would otherwise be regenerated (effectively randomly) on every
 * save, making two renders of the exact same {@link TailoredCvDocument}/{@link RenderableCoverLetter}
 * produce different bytes and defeating {@code FileStoragePort}'s same-content idempotency check. No
 * document-info creation/modification date is set, so none is emitted either. PDFBox's TrueType
 * subset-tag generation ({@code TrueTypeEmbedder}) is itself deterministic - a hash of the subsetted
 * glyph set, not random - so embedding a font subset per render does not break byte-identical output
 * for identical input; a new {@link PDDocument} (and therefore a fresh {@link PDType0Font} load) is
 * created per render call, never shared or cached across calls.
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

    private static final float FONT_SIZE_NAME = 17f;
    private static final float FONT_SIZE_HEADLINE = 12.5f;
    private static final float FONT_SIZE_SECTION_HEADING = 13f;
    private static final float FONT_SIZE_BODY = 10.5f;

    private static final float LEADING_NAME = 21f;
    private static final float LEADING_HEADLINE = 16f;
    private static final float LEADING_SECTION_HEADING = 16f;
    private static final float LEADING_BODY = 13f;

    private static final float SECTION_SPACING = 8f;
    private static final float ITEM_SPACING = 5f;
    private static final float INDENT = 12f;
    private static final float RULE_THICKNESS = 0.75f;

    /** Fixed so every render of identical content produces byte-identical output - see class javadoc. */
    private static final long FIXED_DOCUMENT_ID = 1L;

    private static final String FONT_RESOURCE_REGULAR = "fonts/ttf/OpenSans/OpenSans-Regular.ttf";
    private static final String FONT_RESOURCE_BOLD = "fonts/ttf/OpenSans/OpenSans-Bold.ttf";
    private static final String FONT_RESOURCE_SEMIBOLD = "fonts/ttf/OpenSans/OpenSans-SemiBold.ttf";
    private static final String FONT_RESOURCE_ITALIC = "fonts/ttf/OpenSans/OpenSans-Italic.ttf";

    /** The four faces loaded for a single render call - {@link PDType0Font} is bound to one {@link PDDocument}, so this cannot be static. */
    private record Fonts(PDType0Font regular, PDType0Font bold, PDType0Font semiBold, PDType0Font italic) {
    }

    @Override
    public RenderedDocument renderCv(TailoredCvDocument cv) {
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
                loadFont(document, FONT_RESOURCE_SEMIBOLD),
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

    private void writeCv(PdfPageCursor cursor, Fonts fonts, TailoredCvDocument cv) throws IOException {
        writeHeader(cursor, fonts, cv.header());
        cursor.addSpacing(SECTION_SPACING);

        if (cv.professionalSummary() != null && !cv.professionalSummary().isBlank()) {
            writeSectionHeading(cursor, fonts, CvSectionHeadings.PROFESSIONAL_SUMMARY);
            cursor.writeWrapped(cv.professionalSummary(), fonts.regular(), FONT_SIZE_BODY, LEADING_BODY, 0);
            cursor.addSpacing(SECTION_SPACING);
        }

        if (!cv.skills().isEmpty()) {
            writeSectionHeading(cursor, fonts, CvSectionHeadings.TECHNICAL_SKILLS);
            cursor.writeWrappedList(cv.skills(), " | ", fonts.regular(), FONT_SIZE_BODY, LEADING_BODY, 0);
            cursor.addSpacing(SECTION_SPACING);
        }

        if (!cv.experience().isEmpty()) {
            writeSectionHeading(cursor, fonts, CvSectionHeadings.PROFESSIONAL_EXPERIENCE);
            for (TailoredCvCompany company : cv.experience()) {
                writeCompany(cursor, fonts, company);
            }
            cursor.addSpacing(SECTION_SPACING - ITEM_SPACING);
        }

        if (!cv.personalProjects().isEmpty()) {
            writeSectionHeading(cursor, fonts, CvSectionHeadings.PERSONAL_PROJECTS);
            for (TailoredCvPersonalProject project : cv.personalProjects()) {
                writePersonalProject(cursor, fonts, project);
            }
            cursor.addSpacing(SECTION_SPACING - ITEM_SPACING);
        }

        if (!cv.education().isEmpty()) {
            writeSectionHeading(cursor, fonts, CvSectionHeadings.EDUCATION);
            for (CandidateEducationFacts education : cv.education()) {
                writeEducation(cursor, fonts, education);
            }
            cursor.addSpacing(SECTION_SPACING - ITEM_SPACING);
        }

        if (!cv.languages().isEmpty()) {
            writeSectionHeading(cursor, fonts, CvSectionHeadings.LANGUAGES);
            for (CandidateLanguageFacts language : cv.languages()) {
                cursor.writeLine(formatLanguage(language), fonts.regular(), FONT_SIZE_BODY, LEADING_BODY, 0);
            }
        }
    }

    private void writeHeader(PdfPageCursor cursor, Fonts fonts, TailoredCvHeader header) throws IOException {
        if (header.fullName() != null && !header.fullName().isBlank()) {
            cursor.writeLine(header.fullName(), fonts.bold(), FONT_SIZE_NAME, LEADING_NAME, 0);
        }
        if (header.cvHeadline() != null && !header.cvHeadline().isBlank()) {
            cursor.writeLine(header.cvHeadline(), fonts.semiBold(), FONT_SIZE_HEADLINE, LEADING_HEADLINE, 0);
        }
        String contactLine = formatContactLine(header);
        if (!contactLine.isBlank()) {
            cursor.writeWrapped(contactLine, fonts.regular(), FONT_SIZE_BODY, LEADING_BODY, 0);
        }
        if (header.linkedinUrl() != null && !header.linkedinUrl().isBlank()) {
            String display = "LinkedIn: " + CvUrlDisplay.displayText(header.linkedinUrl());
            cursor.writeLineWithLink(display, fonts.regular(), FONT_SIZE_BODY, LEADING_BODY, 0, CvUrlDisplay.hyperlinkTarget(header.linkedinUrl()));
        }
    }

    private String formatContactLine(TailoredCvHeader header) {
        StringBuilder line = new StringBuilder();
        appendIfPresent(line, header.cvLocation());
        appendIfPresent(line, header.phone());
        appendIfPresent(line, header.email());
        return line.toString();
    }

    private void writeCompany(PdfPageCursor cursor, Fonts fonts, TailoredCvCompany company) throws IOException {
        cursor.writeWrapped(company.name(), fonts.semiBold(), FONT_SIZE_BODY, LEADING_BODY, 0);
        for (TailoredCvPosition position : company.positions()) {
            writePosition(cursor, fonts, position);
        }
        cursor.addSpacing(ITEM_SPACING);
    }

    private void writePosition(PdfPageCursor cursor, Fonts fonts, TailoredCvPosition position) throws IOException {
        String dateRange = CvDateRangeFormatter.format(position.startDate(), position.endDate(), position.currentRole());
        String titleLine = dateRange.isBlank() ? position.title() : position.title() + " | " + dateRange;
        cursor.writeWrapped(titleLine, fonts.bold(), FONT_SIZE_BODY, LEADING_BODY, INDENT);

        String meta = formatPositionMeta(position);
        if (!meta.isBlank()) {
            cursor.writeWrapped(meta, fonts.italic(), FONT_SIZE_BODY, LEADING_BODY, INDENT);
        }
        if (position.description() != null && !position.description().isBlank()) {
            cursor.writeWrapped(position.description(), fonts.regular(), FONT_SIZE_BODY, LEADING_BODY, INDENT);
        }
        for (String responsibility : position.responsibilities()) {
            cursor.writeWrapped("- " + responsibility, fonts.regular(), FONT_SIZE_BODY, LEADING_BODY, INDENT * 2);
        }
        for (String achievement : position.achievements()) {
            cursor.writeWrapped("- " + achievement, fonts.regular(), FONT_SIZE_BODY, LEADING_BODY, INDENT * 2);
        }
        for (TailoredCvProject project : position.projects()) {
            writeProject(cursor, fonts, project);
        }
        cursor.addSpacing(ITEM_SPACING);
    }

    private String formatPositionMeta(TailoredCvPosition position) {
        StringBuilder meta = new StringBuilder();
        appendIfPresent(meta, position.employmentType());
        appendIfPresent(meta, position.workArrangement());
        appendIfPresent(meta, position.location());
        return meta.toString();
    }

    private void writeProject(PdfPageCursor cursor, Fonts fonts, TailoredCvProject project) throws IOException {
        String dateRange = CvDateRangeFormatter.format(project.startDate(), project.endDate(), false);
        String line = dateRange.isBlank() ? "Project: " + project.name() : "Project: " + project.name() + " | " + dateRange;
        cursor.writeWrapped(line, fonts.semiBold(), FONT_SIZE_BODY, LEADING_BODY, INDENT * 2);
        if (project.description() != null && !project.description().isBlank()) {
            cursor.writeWrapped(project.description(), fonts.regular(), FONT_SIZE_BODY, LEADING_BODY, INDENT * 2);
        }
        for (String responsibility : project.responsibilities()) {
            cursor.writeWrapped("- " + responsibility, fonts.regular(), FONT_SIZE_BODY, LEADING_BODY, INDENT * 3);
        }
        for (String achievement : project.achievements()) {
            cursor.writeWrapped("- " + achievement, fonts.regular(), FONT_SIZE_BODY, LEADING_BODY, INDENT * 3);
        }
        if (!project.technologies().isEmpty()) {
            writeTechnologies(cursor, fonts, project.technologies(), INDENT * 2);
        }
    }

    private void writePersonalProject(PdfPageCursor cursor, Fonts fonts, TailoredCvPersonalProject project) throws IOException {
        String dateRange = CvDateRangeFormatter.format(project.startDate(), project.endDate(), false);
        String line = dateRange.isBlank() ? project.name() : project.name() + " | " + dateRange;
        cursor.writeWrapped(line, fonts.semiBold(), FONT_SIZE_BODY, LEADING_BODY, INDENT);
        if (project.description() != null && !project.description().isBlank()) {
            cursor.writeWrapped(project.description(), fonts.regular(), FONT_SIZE_BODY, LEADING_BODY, INDENT);
        }
        if (project.url() != null && !project.url().isBlank()) {
            cursor.writeLineWithLink(CvUrlDisplay.displayText(project.url()), fonts.regular(), FONT_SIZE_BODY, LEADING_BODY, INDENT,
                    CvUrlDisplay.hyperlinkTarget(project.url()));
        }
        for (String highlight : project.highlights()) {
            cursor.writeWrapped("- " + highlight, fonts.regular(), FONT_SIZE_BODY, LEADING_BODY, INDENT * 2);
        }
        if (!project.technologies().isEmpty()) {
            writeTechnologies(cursor, fonts, project.technologies(), INDENT);
        }
        cursor.addSpacing(ITEM_SPACING);
    }

    /**
     * Writes a "Technologies: " label followed by {@code technologies}, wrapped at technology-name
     * boundaries via {@link PdfPageCursor#writeWrappedList} rather than word boundaries - a
     * multi-word technology name (e.g. {@code "Cassandra Java Driver"}) must never be split across a
     * wrapped line, matching the Technical Skills fix for the identical defect (see {@link
     * PdfPageCursor#writeWrappedList}'s javadoc). The label is glued to the first technology as one
     * atom so it is never separated from it by a wrap either.
     */
    private void writeTechnologies(PdfPageCursor cursor, Fonts fonts, List<String> technologies, float indent) throws IOException {
        List<String> items = new ArrayList<>(technologies.size());
        items.add("Technologies: " + technologies.get(0));
        items.addAll(technologies.subList(1, technologies.size()));
        cursor.writeWrappedList(items, ", ", fonts.italic(), FONT_SIZE_BODY, LEADING_BODY, indent);
    }

    private void writeEducation(PdfPageCursor cursor, Fonts fonts, CandidateEducationFacts education) throws IOException {
        cursor.writeWrapped(education.institution(), fonts.semiBold(), FONT_SIZE_BODY, LEADING_BODY, 0);
        StringBuilder meta = new StringBuilder();
        appendIfPresent(meta, education.degree());
        appendIfPresent(meta, education.fieldOfStudy());
        appendIfPresent(meta, education.location());
        String dateRange = CvDateRangeFormatter.format(education.startDate(), education.endDate(), false);
        appendIfPresent(meta, dateRange.isBlank() ? null : dateRange);
        if (!meta.isEmpty()) {
            cursor.writeWrapped(meta.toString(), fonts.regular(), FONT_SIZE_BODY, LEADING_BODY, 0);
        }
        if (education.description() != null && !education.description().isBlank()) {
            cursor.writeWrapped(education.description(), fonts.regular(), FONT_SIZE_BODY, LEADING_BODY, 0);
        }
        cursor.addSpacing(ITEM_SPACING);
    }

    private String formatLanguage(CandidateLanguageFacts language) {
        return language.proficiency() == null ? language.name() : language.name() + ": " + language.proficiency();
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

    // ==================== Shared ====================

    private void writeSectionHeading(PdfPageCursor cursor, Fonts fonts, String text) throws IOException {
        cursor.writeLine(text, fonts.bold(), FONT_SIZE_SECTION_HEADING, LEADING_SECTION_HEADING, 0);
        cursor.drawHorizontalRule(RULE_THICKNESS);
    }

    private void appendIfPresent(StringBuilder builder, String value) {
        if (value != null && !value.isBlank()) {
            if (!builder.isEmpty()) {
                builder.append(" | ");
            }
            builder.append(value);
        }
    }
}

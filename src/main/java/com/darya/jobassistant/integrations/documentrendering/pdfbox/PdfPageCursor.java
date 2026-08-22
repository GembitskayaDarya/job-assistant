package com.darya.jobassistant.integrations.documentrendering.pdfbox;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;

/**
 * Sprint 10 Step 4: single-column text layout cursor shared by the CV and cover letter writers -
 * plain vector text drawn directly at measured positions (never a rasterized page image, keeping
 * output selectable/searchable and ATS-friendly), with automatic, predictable page breaks: a new
 * page starts whenever the next line would cross the bottom margin, never mid-line. Deterministic
 * for a given input - the same text at the same font/size always wraps and paginates the same way.
 */
final class PdfPageCursor implements Closeable {

    private final PDDocument document;
    private final PDRectangle pageSize;
    private final float leftMargin;
    private final float rightMargin;
    private final float topMargin;
    private final float bottomMargin;

    private PDPageContentStream contentStream;
    private PDPage currentPage;
    private float cursorY;

    PdfPageCursor(PDDocument document, PDRectangle pageSize, float leftMargin, float rightMargin, float topMargin, float bottomMargin)
            throws IOException {
        this.document = document;
        this.pageSize = pageSize;
        this.leftMargin = leftMargin;
        this.rightMargin = rightMargin;
        this.topMargin = topMargin;
        this.bottomMargin = bottomMargin;
        startNewPage();
    }

    float contentWidth() {
        return pageSize.getWidth() - leftMargin - rightMargin;
    }

    void addSpacing(float amount) {
        cursorY -= amount;
    }

    /** Current vertical position, exposed read-only for layout-invariant tests - never mutated from outside this class. */
    float cursorY() {
        return cursorY;
    }

    /** {@link #bottomMargin}, exposed read-only for layout-invariant tests. */
    float bottomMargin() {
        return bottomMargin;
    }

    /** {@link #topMargin}, exposed read-only for layout-invariant tests (e.g. confirming a fresh page starts with a positive top margin). */
    float topMargin() {
        return topMargin;
    }

    /** {@link #pageSize}'s height, exposed read-only for layout-invariant tests. */
    float pageHeight() {
        return pageSize.getHeight();
    }

    /** {@link #pageSize}'s width, exposed read-only - used by the header background block, which deliberately spans the full page width, not just {@link #contentWidth()}. */
    float pageWidth() {
        return pageSize.getWidth();
    }

    /**
     * Whether {@code height} of content would still fit above {@link #bottomMargin} without a page
     * break - a pure peek, no side effect. Used to decide whether to force a page break before a
     * whole logical block (e.g. a section heading plus its first line of content) rather than letting
     * an automatic per-line {@link #ensureSpace} split it awkwardly partway through.
     */
    boolean hasRoomFor(float height) {
        return cursorY - height >= bottomMargin;
    }

    /**
     * Forces a page break now if {@code height} of content would not otherwise fit above {@link
     * #bottomMargin} - see {@link #hasRoomFor}. Orphan-prevention primitive (Sprint 11 Final
     * Application Package Quality Hardening): a caller estimates the minimum height of one logical
     * block it is about to draw (e.g. a section heading + its rule + one line of content, or a
     * position's title + meta line) and calls this first, so that block is never split by an
     * automatic mid-block page break - the whole block starts fresh on the next page instead. Never
     * used for an entire section's full content (which may legitimately span a page break internally)
     * - only for the small "heading must not be orphaned from what follows it" guarantee.
     */
    void ensureRoomFor(float height) throws IOException {
        if (!hasRoomFor(height)) {
            startNewPage();
        }
    }

    /**
     * Sprint 11 Golden Master CV Reproduction: unconditionally starts a new page, regardless of how
     * much room remains - an explicit, always-applied structural break for one approved CV template
     * (e.g. the golden master's fixed page-2 start), never an orphan-prevention heuristic. {@link
     * #ensureRoomFor} decides "does this still fit"; this method never asks that question.
     */
    void forcePageBreak() throws IOException {
        startNewPage();
    }

    /** Writes one already-fitting line at {@code indent} from the left margin - never wrapped or measured. */
    void writeLine(String text, PDType0Font font, float fontSize, float leading, float indent) throws IOException {
        ensureSpace(leading);
        String safe = PdfTextSanitizer.sanitize(text, font);
        contentStream.beginText();
        contentStream.setFont(font, fontSize);
        contentStream.newLineAtOffset(leftMargin + indent, cursorY);
        contentStream.showText(safe);
        contentStream.endText();
        cursorY -= leading;
    }

    /** Word-wraps {@code text} to fit within {@link #contentWidth()} minus {@code indent}, writing one {@link #writeLine} per wrapped line. */
    void writeWrapped(String text, PDType0Font font, float fontSize, float leading, float indent) throws IOException {
        for (String line : wrap(PdfTextSanitizer.sanitize(text, font), font, fontSize, contentWidth() - indent)) {
            writeLine(line, font, fontSize, leading, indent);
        }
    }

    /**
     * Wraps a {@code separator}-joined list of atomic {@code items} to fit within {@link
     * #contentWidth()} minus {@code indent}, writing one {@link #writeLine} per wrapped line - each
     * item in {@code items} is treated as indivisible and never split across a wrapped line, even
     * when an item itself contains internal whitespace (e.g. a multi-word skill or technology name
     * such as {@code "Oracle Database"}).
     *
     * <p>Unlike {@link #writeWrapped}, which treats every individual space-separated word as its own
     * wrappable unit, this exists specifically for content the rest of the pipeline (e.g. {@code
     * AtsCvVerifier}) may later search for as an exact substring: joining a skills/technologies list
     * with a separator and then word-wrapping the flat result (the previous behavior for both call
     * sites) can place a wrap boundary in the middle of a multi-word item - PDFBox then extracts that
     * item's two halves onto separate lines joined by a newline rather than the original space,
     * silently breaking any exact-phrase search against it. This method wraps at item boundaries
     * instead, so no item's own text is ever altered by where a line happens to break.
     */
    void writeWrappedList(List<String> items, String separator, PDType0Font font, float fontSize, float leading, float indent)
            throws IOException {
        for (String line : wrapAtoms(items, separator, font, fontSize, contentWidth() - indent)) {
            writeLine(line, font, fontSize, leading, indent);
        }
    }

    /**
     * Writes one already-fitting line exactly like {@link #writeLine}, additionally attaching an
     * invisible-border {@link PDAnnotationLink} covering the drawn text's bounding box, pointing at
     * {@code url} - the sole hyperlink mechanism this renderer uses (see {@code
     * PdfBoxApplicationMaterialDocumentRenderer}'s "Hyperlinks" section). A blank/null {@code url}
     * falls back to a plain {@link #writeLine} with no annotation.
     */
    void writeLineWithLink(String text, PDType0Font font, float fontSize, float leading, float indent, String url) throws IOException {
        if (url == null || url.isBlank()) {
            writeLine(text, font, fontSize, leading, indent);
            return;
        }
        ensureSpace(leading);
        String safe = PdfTextSanitizer.sanitize(text, font);
        float x = leftMargin + indent;
        float baselineY = cursorY;
        contentStream.beginText();
        contentStream.setFont(font, fontSize);
        contentStream.newLineAtOffset(x, baselineY);
        contentStream.showText(safe);
        contentStream.endText();
        addLinkAnnotation(x, baselineY, width(safe, font, fontSize), fontSize, url);
        cursorY -= leading;
    }

    /**
     * Sprint 11 Golden Master CV Lock: draws one bulleted item (a real {@code "• "} glyph, not
     * a hyphen) with a proper hanging indent - the bullet only prefixes the first wrapped line;
     * every continuation line aligns under the text, not under the bullet. Mirrors {@link
     * #writeWrapped}'s greedy word-wrap exactly, reserving the bullet's own width from the
     * available line width up front.
     */
    void writeBulletedItem(String text, PDType0Font font, float fontSize, float leading, float indent) throws IOException {
        String bullet = "• ";
        float bulletWidth = width(bullet, font, fontSize);
        List<String> lines = wrap(PdfTextSanitizer.sanitize(text, font), font, fontSize, contentWidth() - indent - bulletWidth);
        boolean first = true;
        for (String line : lines) {
            if (first) {
                writeLine(bullet + line, font, fontSize, leading, indent);
                first = false;
            } else {
                writeLine(line, font, fontSize, leading, indent + bulletWidth);
            }
        }
    }

    /**
     * Sprint 11 Golden Master CV Lock: fills a {@code height}-tall, full-page-width light-gray
     * rectangle immediately below the current cursor position - the header background block the
     * golden-master reference CV uses. Deliberately spans {@link #pageWidth()} edge to edge, not
     * just {@link #contentWidth()}, matching the reference exactly; header text itself still starts
     * at the normal left margin, simply drawn on top of this (painted first, since PDF content
     * paints in draw order). Does not move {@link #cursorY} - purely a background paint, the caller
     * still owns all vertical layout via the usual {@link #writeLine}/{@link #addSpacing} calls.
     */
    void fillHeaderBackground(float height, float gray) throws IOException {
        contentStream.setNonStrokingColor(gray, gray, gray);
        contentStream.addRect(0f, cursorY - height, pageSize.getWidth(), height);
        contentStream.fill();
        contentStream.setNonStrokingColor(0f, 0f, 0f);
    }

    /**
     * Sprint 11 Golden Master CV Lock: writes one already-fitting line made of a plain {@code
     * prefix} followed immediately by a hyperlinked {@code linkedText} segment - the header contact
     * line's LinkedIn entry, which the golden-master reference CV shows inline with location/phone/
     * email on one pipe-separated line, not on its own separate line. Only {@code linkedText} gets
     * the clickable annotation; {@code prefix} is ordinary text. Never wrapped - this is used for
     * one specific, always-short-enough line.
     */
    void writeLineWithTrailingLink(
            String prefix, String linkedText, PDType0Font font, float fontSize, float leading, float indent, String url)
            throws IOException {
        ensureSpace(leading);
        String safePrefix = PdfTextSanitizer.sanitize(prefix, font);
        String safeLinked = PdfTextSanitizer.sanitize(linkedText, font);
        float x = leftMargin + indent;
        float baselineY = cursorY;
        contentStream.beginText();
        contentStream.setFont(font, fontSize);
        contentStream.newLineAtOffset(x, baselineY);
        contentStream.showText(safePrefix + safeLinked);
        contentStream.endText();
        float prefixWidth = width(safePrefix, font, fontSize);
        if (url != null && !url.isBlank()) {
            addLinkAnnotation(x + prefixWidth, baselineY, width(safeLinked, font, fontSize), fontSize, url);
        }
        cursorY -= leading;
    }

    /**
     * Draws one thin horizontal rule spanning {@link #contentWidth()} at the current cursor position,
     * then advances by exactly {@code thickness} - nothing more. Deliberately honest/minimal (Sprint
     * 11 Final Application Package Quality Hardening production fix): this used to silently advance by
     * {@code thickness + 4f}, an undocumented fixed gap baked into the primitive itself rather than a
     * caller decision - the exact "horizontal rule positioned as a side effect of an approximate
     * cursor offset" defect this fixes. Every gap around a rule (before it, after it) is now the
     * caller's own explicit {@link #addSpacing} call, using named layout-policy constants - see {@code
     * PdfBoxApplicationMaterialDocumentRenderer#writeSectionHeading}.
     *
     * <p>Sprint 11 Golden Master CV Reproduction: draws a filled rectangle, not a stroked line -
     * measured directly from the golden master's own content stream (a {@code PDFGraphicsStreamEngine}
     * diagnostic against {@code config/private/cv/golden-master/Darya_Hembitskaya_CV.pdf} recovered
     * every rule as a filled bar extending downward from the current position by its own height, at
     * consistent left/right X across every rule on both pages), not a centered stroke. A stroked line
     * of the same nominal width paints half above and half below the path, which is a different
     * (thinner-looking, differently-positioned) visual than the reference's solid bar.
     */
    void drawHorizontalRule(float thickness) throws IOException {
        ensureSpace(thickness);
        contentStream.addRect(leftMargin, cursorY - thickness, contentWidth(), thickness);
        contentStream.fill();
        cursorY -= thickness;
    }

    private void addLinkAnnotation(float x, float baselineY, float textWidth, float fontSize, String url) throws IOException {
        PDAnnotationLink link = new PDAnnotationLink();
        PDRectangle position = new PDRectangle();
        position.setLowerLeftX(x);
        position.setLowerLeftY(baselineY - 2f);
        position.setUpperRightX(x + textWidth);
        position.setUpperRightY(baselineY + fontSize);
        link.setRectangle(position);
        PDBorderStyleDictionary invisibleBorder = new PDBorderStyleDictionary();
        invisibleBorder.setWidth(0);
        link.setBorderStyle(invisibleBorder);
        PDActionURI action = new PDActionURI();
        action.setURI(url);
        link.setAction(action);
        currentPage.getAnnotations().add(link);
    }

    private void ensureSpace(float needed) throws IOException {
        if (cursorY - needed < bottomMargin) {
            startNewPage();
        }
    }

    private void startNewPage() throws IOException {
        if (contentStream != null) {
            contentStream.close();
        }
        PDPage page = new PDPage(pageSize);
        document.addPage(page);
        currentPage = page;
        contentStream = new PDPageContentStream(document, page);
        cursorY = pageSize.getHeight() - topMargin;
    }

    /**
     * Greedy word wrap using the font's own glyph-width metrics ({@link PDType0Font#getStringWidth}) -
     * deterministic for a given font/size/width. A single word wider than {@code maxWidth} on its
     * own is placed on its own line rather than split mid-word or dropped.
     */
    private List<String> wrap(String text, PDType0Font font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String paragraphLine : text.split("\n", -1)) {
            lines.addAll(wrapSingleLine(paragraphLine, font, fontSize, maxWidth));
        }
        return lines;
    }

    /**
     * Greedy packing of already-atomic {@code items} onto lines, joined by {@code separator} within
     * a line - mirrors {@link #wrapSingleLine}'s greedy-fit algorithm exactly, but the unit that may
     * never be split is one list item (however many words it contains) rather than one
     * whitespace-delimited word. A single item wider than {@code maxWidth} on its own is placed on
     * its own line rather than split or dropped, matching {@link #wrapSingleLine}'s same guarantee.
     * Each item is sanitized once up front so the width measured here matches exactly what {@link
     * #writeLine} later draws (which sanitizes again, harmlessly - {@link PdfTextSanitizer#sanitize}
     * is idempotent).
     */
    private List<String> wrapAtoms(List<String> items, String separator, PDType0Font font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String rawItem : items) {
            String item = PdfTextSanitizer.sanitize(rawItem, font);
            String candidate = current.isEmpty() ? item : current + separator + item;
            if (width(candidate, font, fontSize) <= maxWidth || current.isEmpty()) {
                current = new StringBuilder(candidate);
            } else {
                lines.add(current.toString());
                current = new StringBuilder(item);
            }
        }
        if (!current.isEmpty() || lines.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private List<String> wrapSingleLine(String text, PDType0Font font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (width(candidate, font, fontSize) <= maxWidth || current.isEmpty()) {
                current = new StringBuilder(candidate);
            } else {
                lines.add(current.toString());
                current = new StringBuilder(word);
            }
        }
        lines.add(current.toString());
        return lines;
    }

    private float width(String text, PDType0Font font, float fontSize) throws IOException {
        return font.getStringWidth(text) / 1000f * fontSize;
    }

    @Override
    public void close() throws IOException {
        if (contentStream != null) {
            contentStream.close();
        }
    }
}

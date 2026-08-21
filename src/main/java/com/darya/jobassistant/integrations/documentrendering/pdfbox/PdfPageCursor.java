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

    /** Draws one thin horizontal rule spanning {@link #contentWidth()} at the current cursor position, then advances past it. */
    void drawHorizontalRule(float thickness) throws IOException {
        ensureSpace(thickness + 4f);
        contentStream.setLineWidth(thickness);
        contentStream.moveTo(leftMargin, cursorY);
        contentStream.lineTo(leftMargin + contentWidth(), cursorY);
        contentStream.stroke();
        cursorY -= (thickness + 4f);
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

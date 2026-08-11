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

package com.darya.jobassistant.integrations.documentrendering.pdfbox.goldenmaster;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.util.Matrix;

/**
 * Sprint 11 Golden Master Template Rendering: performs the actual content-stream surgery - the
 * golden master's page 1 is loaded as-is and only its Technical Skills value token block(s) (as
 * found by {@link TechnicalSkillsRegionLocator}) are removed and replaced. Every other byte of
 * template content (rules, margins, header, Professional Experience, Netcracker's page break,
 * Mentoring, Personal Project, Education, Languages, page 2 in full) is carried through completely
 * untouched - this class never draws, measures, or lays out any of it.
 *
 * <h2>Genuine removal, not visual masking</h2>
 *
 * The old value's {@code BT...Tm...Tf...TJ...ET} token block is deleted outright from the parsed
 * token list before the page's content stream is re-serialized - the old glyph-show operator and its
 * operands are gone from the PDF, not merely painted over, so {@link
 * org.apache.pdfbox.text.PDFTextStripper}/ATS text extraction can never recover it. See {@link
 * TechnicalSkillsRegionLocator}'s cross-validated block matching for how those exact tokens are
 * found without brittle string matching (the template's embedded font uses CID/Identity-H encoding,
 * so a {@code Tj}/{@code TJ} operand's raw bytes are not even readable text to match against).
 *
 * <h2>Font</h2>
 *
 * The replacement text is drawn with a freshly-loaded, freshly-subsetted {@link PDType0Font} of
 * Inter Regular (the same vendored file {@code PdfBoxApplicationMaterialDocumentRenderer} uses),
 * registered as a brand-new resource - never the template's own embedded font subset, which only
 * contains glyphs for the original approved skill text and would be missing glyphs for a new skill
 * name that never appeared there.
 *
 * <h2>Insertion: draw for real, then reorder tokens</h2>
 *
 * Two properties are both required, and empirically do not come for free from any single, simpler
 * approach: (1) the replacement must be genuinely extractable text - the shown codepoints must be
 * registered with the font's subsetter ({@link PDType0Font#addToSubset}) so the embedded subset's
 * glyphs and {@code ToUnicode} CMap actually cover them, which only happens when text is drawn
 * through PDFBox's own {@link org.apache.pdfbox.pdmodel.PDAbstractContentStream#showText} against a
 * content stream genuinely attached to the real page (a hand-built raw {@code Tj} token, and even
 * {@code showText} against a detached scratch {@link org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject}
 * later spliced in by bytes, were both tried and both extract as mojibake - PDFBox's font-subset
 * finalization at {@link PDDocument#save} apparently does not reliably cover a font only ever
 * referenced that way); and (2) the replacement must land at the correct position in {@link
 * org.apache.pdfbox.text.PDFTextStripper}'s reading order - immediately after the Technical Skills
 * heading, not wherever it happens to be appended - which requires it to occupy the exact
 * token-stream position the removed block did (content appended via {@code
 * AppendMode#APPEND} always paints last within its page in content-stream order, which
 * PDFTextStripper follows by default).
 *
 * <p>{@link #render} resolves both by doing each in the mechanism already proven to get it right,
 * then combining the results at the token level: {@link #drawReplacementDirectlyOnPage} draws
 * through a real {@code AppendMode.APPEND} content stream attached to the actual page (correct
 * subset/{@code ToUnicode}), then the page is re-parsed as a whole - which naturally concatenates
 * both content streams into one token list, with the freshly-drawn block's tokens at the end -
 * and {@link #reorderNewBlockToCorrectPosition} moves exactly that trailing block (identified by
 * how many tokens existed before it was drawn) to the position the old value occupied, reusing
 * {@link TechnicalSkillsRegion#insertionTx()}/{@link TechnicalSkillsRegion#insertionTy()} so it
 * still executes under the same {@code cm} transform chain every other row on the page does.
 */
final class GoldenMasterCvRenderer {

    private static final String FONT_RESOURCE_REGULAR = "fonts/ttf/Inter/Inter-Regular.ttf";
    private static final String SKILLS_SEPARATOR = " | ";
    private static final long FIXED_DOCUMENT_ID = 1L;

    private GoldenMasterCvRenderer() {
    }

    record Result(byte[] pdfBytes, List<String> renderedSkills) {
    }

    static Result render(PDDocument document, List<String> skills) throws IOException {
        document.setDocumentId(FIXED_DOCUMENT_ID);
        PDPage page = document.getPage(0);
        TechnicalSkillsRegion region = TechnicalSkillsRegionLocator.locate(document);
        PDType0Font font = loadFont(document);
        List<String> fitted = GoldenMasterCvSkillsFitPolicy.fitToOneLine(skills, region, font);
        String text = String.join(SKILLS_SEPARATOR, fitted);

        int tokenCountBeforeDrawing = new PDFStreamParser(page).parse().size();
        drawReplacementDirectlyOnPage(document, page, font, region, text);

        List<Object> tokens = new ArrayList<>(new PDFStreamParser(page).parse());
        List<Object> newBlock = new ArrayList<>(tokens.subList(tokenCountBeforeDrawing, tokens.size()));
        tokens.subList(tokenCountBeforeDrawing, tokens.size()).clear();

        int insertAt = removeTokenRanges(tokens, region.tokenRangesToRemove());
        tokens.addAll(insertAt, newBlock);

        writeContentStream(document, page, tokens);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return new Result(out.toByteArray(), fitted);
    }

    /** Draws via a real, page-attached {@code AppendMode.APPEND} content stream - see class javadoc for why this is the only mechanism proven to correctly register the shown text for subsetting/{@code ToUnicode}. Lands as a trailing content-stream entry; {@link #render} moves it to the correct position afterward. */
    private static void drawReplacementDirectlyOnPage(PDDocument document, PDPage page, PDType0Font font, TechnicalSkillsRegion region, String text) throws IOException {
        try (PDPageContentStream cs = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, false, false)) {
            cs.beginText();
            cs.setFont(font, region.rawFontSize());
            cs.setTextMatrix(new Matrix(1, 0, 0, -1, region.insertionTx(), region.insertionTy()));
            cs.showText(text);
            cs.endText();
        }
    }

    /** Removes every range (descending order, so earlier indices stay valid) and returns the smallest range's original start index - the correct spot to insert the replacement, since nothing before it was ever touched. */
    private static int removeTokenRanges(List<Object> tokens, List<int[]> ranges) {
        List<int[]> sorted = new ArrayList<>(ranges);
        sorted.sort((a, b) -> Integer.compare(b[0], a[0]));
        for (int[] range : sorted) {
            tokens.subList(range[0], range[1] + 1).clear();
        }
        return ranges.stream().mapToInt(r -> r[0]).min().orElseThrow();
    }

    private static void writeContentStream(PDDocument document, PDPage page, List<Object> tokens) throws IOException {
        PDStream newStream = new PDStream(document);
        try (OutputStream out = newStream.createOutputStream()) {
            new ContentStreamWriter(out).writeTokens(tokens);
        }
        page.setContents(newStream);
    }

    /** {@code embedSubset=true}, matching {@code PdfBoxApplicationMaterialDocumentRenderer}'s convention - see that class's "Fonts" javadoc for full Inter provenance/license detail. */
    private static PDType0Font loadFont(PDDocument document) throws IOException {
        try (InputStream fontStream = GoldenMasterCvRenderer.class.getClassLoader().getResourceAsStream(FONT_RESOURCE_REGULAR)) {
            if (fontStream == null) {
                throw new IOException("Bundled font resource not found on classpath: " + FONT_RESOURCE_REGULAR);
            }
            return PDType0Font.load(document, fontStream, true);
        }
    }
}

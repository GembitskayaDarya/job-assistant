package com.darya.jobassistant.integrations.documentrendering.pdfbox.goldenmaster;

import com.darya.jobassistant.applicationmaterials.render.model.CvSectionHeadings;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;

/**
 * Sprint 11 Golden Master Template Rendering: finds exactly which page-1 content-stream text-show
 * tokens draw the Technical Skills value line(s), and the geometry a replacement must reuse - so
 * {@link GoldenMasterCvRenderer} never has to guess a position or hardcode a coordinate measured
 * once from one specific file.
 *
 * <h2>Method</h2>
 *
 * <ol>
 *   <li>Extract every visual text row on page 1 via {@link PDFTextStripper} (ground truth - PDFBox
 *       resolves whatever content-stream transform structure produced each row's real device
 *       position and nominal font size for us, regardless of that structure's shape).
 *   <li>Locate the {@link CvSectionHeadings#TECHNICAL_SKILLS} heading row, and the next row after it
 *       whose text matches any other {@link CvSectionHeadings} constant - the rows strictly between
 *       them, left-aligned to the heading's own left edge, are the Technical Skills value row(s).
 *   <li>Independently walk the raw content-stream tokens, tracking the actual current transformation
 *       matrix (composed from every {@code q}/{@code cm}/{@code Q} operator, exactly as a PDF
 *       consumer would - see {@link #locate}) so each {@code BT...Tm...ET} block's own device
 *       position can be computed directly from its raw {@code Tm} operands, independent of the
 *       {@link PDFTextStripper} pass above.
 *   <li>Match each block position against the value rows found in step 2 (small position epsilon,
 *       to absorb float rounding through two independent computations) - this is the cross-
 *       validation the class javadoc promises: the two independently-derived positions must agree
 *       before any token range is trusted enough to remove. A block containing a mix of matched and
 *       unmatched {@code Tm} positions, or a value row matched by zero blocks, means the template's
 *       structure is not what this class assumes and is a {@link GoldenMasterCvTemplateException},
 *       never a best-effort guess.
 * </ol>
 *
 * <p>Never assumes a specific numeric scale/flip (the current golden master happens to use a
 * {@code [1,0,0,-1,0,H] cm} then {@code [0.75,0,0,0.75,0,0] cm} pair before its first {@code BT},
 * but this class derives whatever transform is actually present by tracking every {@code q}/{@code
 * cm}/{@code Q} it encounters, not by hardcoding those two specific matrices).
 */
final class TechnicalSkillsRegionLocator {

    /** Position-matching tolerance (points) between the PDFTextStripper-measured row position and the token-derived block position - float rounding through two independent computation paths, not a real ambiguity margin. */
    private static final float POSITION_EPSILON = 1.5f;

    private TechnicalSkillsRegionLocator() {
    }

    static TechnicalSkillsRegion locate(PDDocument document) throws IOException {
        PDPage page = document.getPage(0);
        List<Row> rows = extractRows(document);

        int headingIndex = findRowIndex(rows, CvSectionHeadings.TECHNICAL_SKILLS, 0);
        if (headingIndex < 0) {
            throw new GoldenMasterCvTemplateException(
                    "Golden master template page 1 has no '" + CvSectionHeadings.TECHNICAL_SKILLS + "' heading row");
        }
        int nextHeadingIndex = findAnyHeadingIndex(rows, headingIndex + 1);
        if (nextHeadingIndex < 0) {
            throw new GoldenMasterCvTemplateException(
                    "Golden master template page 1 has no section heading after '" + CvSectionHeadings.TECHNICAL_SKILLS + "'");
        }

        Row headingRow = rows.get(headingIndex);
        List<Row> valueRows = new ArrayList<>();
        for (int i = headingIndex + 1; i < nextHeadingIndex; i++) {
            Row row = rows.get(i);
            if (Math.abs(row.minX() - headingRow.minX()) < POSITION_EPSILON) {
                valueRows.add(row);
            }
        }
        if (valueRows.isEmpty()) {
            throw new GoldenMasterCvTemplateException(
                    "Golden master template's Technical Skills region resolved to zero value rows between "
                            + "'" + CvSectionHeadings.TECHNICAL_SKILLS + "' and the next heading");
        }

        List<int[]> tokenRanges = new ArrayList<>();
        float[] insertionOrigin = new float[2];
        matchTokenBlocks(page, valueRows, tokenRanges, insertionOrigin);

        float marginX = headingRow.minX();
        float availableWidthDevice = page.getMediaBox().getWidth() - 2 * marginX;
        float scaleFactor = deriveScaleFactor(page);
        float localAvailableWidth = availableWidthDevice / scaleFactor;

        return new TechnicalSkillsRegion(
                valueRows.get(0).rawFontSize(), localAvailableWidth, insertionOrigin[0], insertionOrigin[1], tokenRanges);
    }

    // ==================== Ground-truth row extraction ====================

    private record Row(String text, float minX, float y, float rawFontSize) {
    }

    private static List<Row> extractRows(PDDocument document) throws IOException {
        RowStripper stripper = new RowStripper();
        stripper.setStartPage(1);
        stripper.setEndPage(1);
        stripper.getText(document);
        return stripper.rows;
    }

    private static class RowStripper extends PDFTextStripper {
        final List<Row> rows = new ArrayList<>();
        private final StringBuilder currentLine = new StringBuilder();
        private Float minX;
        private Float y;
        private Float rawFontSize;

        RowStripper() throws IOException {
            super();
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) {
            for (TextPosition tp : textPositions) {
                if (minX == null) {
                    minX = tp.getXDirAdj();
                    y = tp.getYDirAdj();
                    rawFontSize = tp.getFontSize();
                }
            }
            currentLine.append(text);
        }

        @Override
        protected void writeLineSeparator() {
            if (!currentLine.isEmpty() && minX != null) {
                rows.add(new Row(currentLine.toString().trim(), minX, y, rawFontSize));
            }
            currentLine.setLength(0);
            minX = null;
            y = null;
            rawFontSize = null;
        }
    }

    private static int findRowIndex(List<Row> rows, String exactText, int fromIndex) {
        for (int i = fromIndex; i < rows.size(); i++) {
            if (rows.get(i).text().equals(exactText)) {
                return i;
            }
        }
        return -1;
    }

    private static final Set<String> ALL_HEADINGS = Set.of(
            CvSectionHeadings.PROFESSIONAL_SUMMARY, CvSectionHeadings.TECHNICAL_SKILLS, CvSectionHeadings.PROFESSIONAL_EXPERIENCE,
            CvSectionHeadings.MENTORING_EXPERIENCE, CvSectionHeadings.PERSONAL_PROJECT, CvSectionHeadings.EDUCATION, CvSectionHeadings.LANGUAGES);

    private static int findAnyHeadingIndex(List<Row> rows, int fromIndex) {
        for (int i = fromIndex; i < rows.size(); i++) {
            if (ALL_HEADINGS.contains(rows.get(i).text())) {
                return i;
            }
        }
        return -1;
    }

    // ==================== Token-stream cross-validation ====================

    /**
     * Walks every token in page 1's content stream once, tracking the real current transformation
     * matrix via a {@code q}/{@code cm}/{@code Q} stack (never assuming a fixed scale - see class
     * javadoc), and matches each self-contained {@code BT...Tm...ET} block's device position against
     * {@code valueRows}. Populates {@code tokenRanges} with every matched block's {@code [btIndex,
     * etIndex]} and {@code insertionOrigin} with the raw {@code Tm} translation ({@code tx, ty}) of
     * the block matching {@code valueRows.get(0)} - the replacement is spliced back into this exact
     * token-stream position (see {@link GoldenMasterCvRenderer}), so it must reuse the same raw,
     * pre-CTM coordinates the removed block used, not a device-space position.
     */
    private static void matchTokenBlocks(PDPage page, List<Row> valueRows, List<int[]> tokenRanges, float[] insertionOrigin) throws IOException {
        List<Object> tokens = new PDFStreamParser(page).parse();
        float pageHeight = page.getMediaBox().getHeight();

        Deque<Matrix> ctmStack = new ArrayDeque<>();
        ctmStack.push(new Matrix());

        boolean[] rowMatched = new boolean[valueRows.size()];
        List<Float> pending = new ArrayList<>();
        int btStart = -1;
        List<float[]> blockTms = new ArrayList<>(); // {tx, ty, deviceX, topDownY}

        for (int i = 0; i < tokens.size(); i++) {
            Object token = tokens.get(i);
            if (token instanceof COSNumber n) {
                pending.add(n.floatValue());
                continue;
            }
            if (!(token instanceof Operator op)) {
                pending.clear();
                continue;
            }
            switch (op.getName()) {
                case "q" -> ctmStack.push(ctmStack.peek().clone());
                case "Q" -> {
                    if (ctmStack.size() > 1) {
                        ctmStack.pop();
                    }
                }
                case "cm" -> {
                    if (pending.size() >= 6) {
                        Matrix cm = new Matrix(pending.get(0), pending.get(1), pending.get(2), pending.get(3), pending.get(4), pending.get(5));
                        ctmStack.peek().concatenate(cm);
                    }
                }
                case "BT" -> {
                    btStart = i;
                    blockTms.clear();
                }
                case "Tm" -> {
                    if (pending.size() >= 6) {
                        float tx = pending.get(pending.size() - 2);
                        float ty = pending.get(pending.size() - 1);
                        java.awt.geom.Point2D.Float device = ctmStack.peek().transformPoint(tx, ty);
                        float topDownY = pageHeight - device.y;
                        blockTms.add(new float[]{tx, ty, device.x, topDownY});
                    }
                }
                case "ET" -> {
                    if (btStart >= 0) {
                        handleBlockClose(btStart, i, blockTms, valueRows, rowMatched, tokenRanges, insertionOrigin);
                    }
                    btStart = -1;
                    blockTms.clear();
                }
                default -> {
                    // no-op: any other operator is irrelevant to CTM/text-position tracking
                }
            }
            pending.clear();
        }

        for (int i = 0; i < rowMatched.length; i++) {
            if (!rowMatched[i]) {
                throw new GoldenMasterCvTemplateException(
                        "Golden master template's Technical Skills value row '" + valueRows.get(i).text()
                                + "' could not be matched to a content-stream token block");
            }
        }
    }

    private static void handleBlockClose(int btStart, int etIndex, List<float[]> blockTms, List<Row> valueRows,
            boolean[] rowMatched, List<int[]> tokenRanges, float[] insertionOrigin) {
        int matchedRowCount = 0;
        int firstMatchedRowIndex = -1;
        for (float[] tm : blockTms) {
            int rowIndex = findMatchingRow(valueRows, tm[2], tm[3]);
            if (rowIndex >= 0) {
                matchedRowCount++;
                if (firstMatchedRowIndex < 0) {
                    firstMatchedRowIndex = rowIndex;
                }
            }
        }
        if (matchedRowCount == 0) {
            return;
        }
        if (matchedRowCount != blockTms.size()) {
            throw new GoldenMasterCvTemplateException(
                    "Golden master template's Technical Skills region overlaps a content-stream block "
                            + "[" + btStart + ".." + etIndex + "] that also contains unrelated text - cannot safely isolate it");
        }
        for (float[] tm : blockTms) {
            int rowIndex = findMatchingRow(valueRows, tm[2], tm[3]);
            rowMatched[rowIndex] = true;
        }
        tokenRanges.add(new int[]{btStart, etIndex});
        if (firstMatchedRowIndex == 0) {
            insertionOrigin[0] = blockTms.get(0)[0];
            insertionOrigin[1] = blockTms.get(0)[1];
        }
    }

    private static int findMatchingRow(List<Row> valueRows, float deviceX, float topDownY) {
        for (int i = 0; i < valueRows.size(); i++) {
            Row row = valueRows.get(i);
            if (Math.abs(row.minX() - deviceX) < POSITION_EPSILON && Math.abs(row.y() - topDownY) < POSITION_EPSILON) {
                return i;
            }
        }
        return -1;
    }

    /** The uniform scale established before page 1's first {@code BT} - see class javadoc; never a hardcoded constant. */
    private static float deriveScaleFactor(PDPage page) throws IOException {
        List<Object> tokens = new PDFStreamParser(page).parse();
        Deque<Matrix> ctmStack = new ArrayDeque<>();
        ctmStack.push(new Matrix());
        List<Float> pending = new ArrayList<>();
        for (Object token : tokens) {
            if (token instanceof COSNumber n) {
                pending.add(n.floatValue());
                continue;
            }
            if (token instanceof Operator op) {
                switch (op.getName()) {
                    case "q" -> ctmStack.push(ctmStack.peek().clone());
                    case "Q" -> {
                        if (ctmStack.size() > 1) {
                            ctmStack.pop();
                        }
                    }
                    case "cm" -> {
                        if (pending.size() >= 6) {
                            Matrix cm = new Matrix(pending.get(0), pending.get(1), pending.get(2), pending.get(3), pending.get(4), pending.get(5));
                            ctmStack.peek().concatenate(cm);
                        }
                    }
                    case "BT" -> {
                        Matrix ctm = ctmStack.peek();
                        return (float) Math.hypot(ctm.getValue(0, 0), ctm.getValue(0, 1));
                    }
                    default -> {
                    }
                }
            }
            pending.clear();
        }
        throw new GoldenMasterCvTemplateException("Golden master template page 1 has no 'BT' operator");
    }
}

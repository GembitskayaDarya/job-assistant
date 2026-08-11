package com.darya.jobassistant.integrations.documentrendering.pdfbox;

import org.apache.pdfbox.pdmodel.font.PDType0Font;

/**
 * Sprint 10 Step 4 (production-readiness fix): makes arbitrary candidate/vacancy/AI-generated text
 * safe to draw with an embedded {@link PDType0Font} - narrow by design, tied to the actual font in
 * use rather than a hardcoded character range. Two things are handled, and nothing else:
 *
 * <ul>
 *   <li>Genuinely invalid/control characters ({@link Character#isISOControl}) are dropped - they
 *       have no visual representation and PDFBox cannot draw them.
 *   <li>A code point the loaded font has no glyph for (checked via {@link PDType0Font#getCmapLookup()}
 *       - PDFBox would otherwise throw {@code IllegalArgumentException} drawing it) is replaced with
 *       {@code '?'}, one character at a time, never the whole string.
 * </ul>
 *
 * <p>Everything else passes through byte-for-byte unchanged - no NFKD decomposition, no accent
 * stripping, no transliteration. Open Sans (see {@code PdfBoxApplicationMaterialDocumentRenderer}'s
 * javadoc for how it is bundled) has native precomposed glyphs for Latin, Latin Extended (including
 * Polish {@code ą ć ę ł ń ó ś ź ż}) and Cyrillic, so real-world candidate/vacancy text in those
 * scripts is preserved exactly, not lossily approximated.
 */
final class PdfTextSanitizer {

    private PdfTextSanitizer() {
    }

    static String sanitize(String text, PDType0Font font) {
        if (text == null) {
            return "";
        }
        StringBuilder result = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            if (Character.isISOControl(codePoint)) {
                continue;
            }
            result.appendCodePoint(hasGlyph(font, codePoint) ? codePoint : '?');
        }
        return result.toString();
    }

    private static boolean hasGlyph(PDType0Font font, int codePoint) {
        try {
            return font.getCmapLookup().getGlyphId(codePoint) != 0;
        } catch (RuntimeException e) {
            return false;
        }
    }
}

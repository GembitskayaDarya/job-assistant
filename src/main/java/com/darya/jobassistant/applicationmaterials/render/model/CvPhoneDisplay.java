package com.darya.jobassistant.applicationmaterials.render.model;

/**
 * The exact visible phone display text used both by {@code PdfBoxApplicationMaterialDocumentRenderer}
 * (to draw {@code "+48 516 188 895"} in the header contact line) and {@code AtsCvVerifier} (to confirm
 * that exact text survives PDF text extraction) - one shared source so the two can never drift apart,
 * the same pattern already used by {@link CvDateRangeFormatter}/{@link CvSectionHeadings}/{@link
 * CvUrlDisplay}. Production fix: this formatting used to live only inside the renderer, so the
 * verifier checked the raw, unformatted phone string - which a real header's phone number (matching
 * the pattern below) never actually appears as in the rendered/extracted text, so ATS verification
 * failed every time a phone number was present.
 */
public final class CvPhoneDisplay {

    private CvPhoneDisplay() {
    }

    /** {@code "+48516188895"} -> {@code "+48 516 188 895"}; any shape that does not match this exact pattern is left unchanged - formatting only, never invents or drops a digit. */
    public static String format(String phone) {
        if (phone == null) {
            return null;
        }
        return phone.replaceFirst("^(\\+\\d{1,3})(\\d{3})(\\d{3})(\\d{3})$", "$1 $2 $3 $4");
    }
}

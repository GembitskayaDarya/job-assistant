package com.darya.jobassistant.applicationmaterials.render.model;

/**
 * Sprint 11 Big Block 7: the exact visible display text used both by {@code
 * PdfBoxApplicationMaterialDocumentRenderer} (to draw a LinkedIn/GitHub URL compactly, e.g. {@code
 * "linkedin.com/in/jane"}) and {@code AtsCvVerifier} (to confirm that exact text survives PDF text
 * extraction) - one shared source so the two can never drift apart. The full URL (including scheme)
 * is always used for the actual hyperlink target; only the on-page text is shortened.
 */
public final class CvUrlDisplay {

    private CvUrlDisplay() {
    }

    /** Strips a leading {@code https://}/{@code http://} for compact, readable on-page display - never used for the hyperlink target itself. */
    public static String displayText(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.regionMatches(true, 0, "https://", 0, 8)) {
            return trimmed.substring(8);
        }
        if (trimmed.regionMatches(true, 0, "http://", 0, 7)) {
            return trimmed.substring(7);
        }
        return trimmed;
    }

    /**
     * Production fix: {@link #displayText(String)} composed with stripping a LinkedIn locale
     * subdomain ({@code "pl.linkedin.com/..."} -> {@code "linkedin.com/..."} - the same profile
     * resolves under any locale subdomain) - used for the header's LinkedIn line specifically. This
     * used to live only inside the renderer as a private method, so {@code AtsCvVerifier} checked the
     * un-stripped text and could never find it once a real LinkedIn URL actually had a locale
     * subdomain.
     */
    public static String linkedInDisplayText(String url) {
        String stripped = displayText(url);
        if (stripped == null) {
            return null;
        }
        return stripped.replaceFirst("(?i)^[a-z]{2}\\.linkedin\\.com", "linkedin.com");
    }

    /** The full, scheme-qualified URL a hyperlink annotation must point at - defaults a bare host/path to {@code https://}. */
    public static String hyperlinkTarget(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.regionMatches(true, 0, "https://", 0, 8) || trimmed.regionMatches(true, 0, "http://", 0, 7)) {
            return trimmed;
        }
        return "https://" + trimmed;
    }
}

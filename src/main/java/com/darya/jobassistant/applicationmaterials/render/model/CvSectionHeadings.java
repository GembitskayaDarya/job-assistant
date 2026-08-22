package com.darya.jobassistant.applicationmaterials.render.model;

/**
 * Sprint 11 Big Block 7 (Golden Master CV Lock): the exact section heading text used both by
 * {@code PdfBoxApplicationMaterialDocumentRenderer} (to draw them) and {@code AtsCvVerifier} (to
 * confirm they survive PDF text extraction) - one shared source so the two can never drift apart.
 * Framework-free, since both consumers live in different, independently boundary-tested packages.
 *
 * <p>Uppercase, and {@link #PERSONAL_PROJECT} singular/{@link #MENTORING_EXPERIENCE} added, to
 * match {@code config/private/cv/golden-master/Darya_Hembitskaya_CV.pdf} exactly - the golden
 * master is the sole authority for this text, not a generic CV-writing convention.
 */
public final class CvSectionHeadings {

    public static final String PROFESSIONAL_SUMMARY = "PROFESSIONAL SUMMARY";
    public static final String TECHNICAL_SKILLS = "TECHNICAL SKILLS";
    public static final String PROFESSIONAL_EXPERIENCE = "PROFESSIONAL EXPERIENCE";
    public static final String MENTORING_EXPERIENCE = "MENTORING EXPERIENCE";
    public static final String PERSONAL_PROJECT = "PERSONAL PROJECT";
    public static final String EDUCATION = "EDUCATION";
    public static final String LANGUAGES = "LANGUAGES";

    private CvSectionHeadings() {
    }
}

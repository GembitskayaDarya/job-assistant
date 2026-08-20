package com.darya.jobassistant.applicationmaterials.render.model;

/**
 * Sprint 11 Big Block 7: the exact section heading text used both by {@code
 * PdfBoxApplicationMaterialDocumentRenderer} (to draw them) and {@code AtsCvVerifier} (to confirm
 * they survive PDF text extraction) - one shared source so the two can never drift apart. Framework-
 * free, since both consumers live in different, independently boundary-tested packages.
 */
public final class CvSectionHeadings {

    public static final String PROFESSIONAL_SUMMARY = "Professional Summary";
    public static final String TECHNICAL_SKILLS = "Technical Skills";
    public static final String PROFESSIONAL_EXPERIENCE = "Professional Experience";
    public static final String PERSONAL_PROJECTS = "Personal Projects";
    public static final String EDUCATION = "Education";
    public static final String LANGUAGES = "Languages";

    private CvSectionHeadings() {
    }
}

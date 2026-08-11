package com.darya.jobassistant.applicationmaterials.render;

import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialType;

/**
 * Sprint 10 Step 4: builds the safe, human-readable, user-visible file name for an artifact -
 * deliberately separate from {@link com.darya.jobassistant.integrations.filestorage.FileStoragePort}'s
 * {@code storageKey} (see {@link RenderApplicationMaterialsUseCase}'s storage-key strategy), which
 * is application-generated from stable ids and never derived from this text.
 *
 * <p>Built from the vacancy title - trusted {@code Vacancy}/{@code JobOffer} metadata, never AI-
 * generated - because {@code CandidateProfile} has no candidate name, address, or other personal
 * field to build a filename from (see {@code RenderableCv}'s javadoc for why none is invented
 * here). Sanitization keeps the result filesystem-safe and readable; it is not itself a security
 * boundary - {@code LocalFileStorageAdapter} enforces that separately against {@code storageKey},
 * never against this name.
 */
final class ApplicationMaterialFileNames {

    private static final int MAX_BASE_LENGTH = 100;
    private static final String FALLBACK_BASE = "Application";

    private ApplicationMaterialFileNames() {
    }

    static String forType(ApplicationMaterialType materialType, String vacancyTitle) {
        String suffix = switch (materialType) {
            case CV -> "CV";
            case COVER_LETTER -> "Cover_Letter";
        };
        return sanitizedBase(vacancyTitle) + "_" + suffix + ".pdf";
    }

    private static String sanitizedBase(String vacancyTitle) {
        String base = vacancyTitle == null ? "" : vacancyTitle.trim().replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isBlank()) {
            base = FALLBACK_BASE;
        }
        return base.length() > MAX_BASE_LENGTH ? base.substring(0, MAX_BASE_LENGTH) : base;
    }
}

package com.darya.jobassistant.applicationmaterials.render.model;

/**
 * Sprint 10 Step 4: which document a render/artifact concerns - deliberately kept separate from
 * {@link ApplicationMaterialFormat} (never combined into names like {@code CV_PDF}) so a future
 * format (e.g. DOCX) can be added without renaming or duplicating this enum.
 */
public enum ApplicationMaterialType {
    CV,
    COVER_LETTER
}

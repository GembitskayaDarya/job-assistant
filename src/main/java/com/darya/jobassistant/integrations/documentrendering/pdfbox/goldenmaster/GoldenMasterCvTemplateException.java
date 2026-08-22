package com.darya.jobassistant.integrations.documentrendering.pdfbox.goldenmaster;

/**
 * Sprint 11 Golden Master Template Rendering: thrown whenever the golden master template PDF is
 * missing, unreadable, or does not have the exact structure {@link TechnicalSkillsRegionLocator}
 * requires (wrong page count, a missing section heading, or a Technical Skills region that cannot be
 * uniquely and unambiguously resolved). Deliberately fatal and never caught to fall back to a
 * different renderer - see {@link GoldenMasterCvTemplate}'s javadoc: a structurally drifted template
 * must fail loudly, not silently degrade to some other CV layout.
 */
public class GoldenMasterCvTemplateException extends RuntimeException {

    public GoldenMasterCvTemplateException(String message) {
        super(message);
    }

    public GoldenMasterCvTemplateException(String message, Throwable cause) {
        super(message, cause);
    }
}

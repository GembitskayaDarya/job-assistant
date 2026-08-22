package com.darya.jobassistant.integrations.documentrendering.pdfbox.goldenmaster;

import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.font.PDFont;

/**
 * Sprint 11 Golden Master Template Rendering: the display-fit cap - a purely geometric, defensive
 * concern layered on top of the existing AI relevance/canonicalization/dedup/eligibility skill
 * selection policy (unchanged upstream), never a replacement for it. The Technical Skills value must
 * physically fit on the golden master's one existing line; this class never shrinks the font below
 * the template's own size and never moves/resizes anything else in the template - the only lever it
 * has is dropping the lowest-priority (trailing) skills from an already relevance-ordered list until
 * what remains fits.
 */
final class GoldenMasterCvSkillsFitPolicy {

    private static final String SEPARATOR = " | ";

    private GoldenMasterCvSkillsFitPolicy() {
    }

    /**
     * Returns the longest prefix of {@code skills} (preserving its given, already-priority-ordered
     * sequence) whose {@code " | "}-joined text fits within {@code region}'s {@link
     * TechnicalSkillsRegion#localAvailableWidth()} at {@link TechnicalSkillsRegion#rawFontSize()}.
     * The full list is returned unchanged if it already fits - the common case for every real skill
     * list measured so far.
     *
     * @throws GoldenMasterCvTemplateException if even a single skill does not fit - the template's
     *     own measured region is too narrow for any usable content, a genuine template anomaly that
     *     must fail loudly rather than render silently overflowing/unreadable text
     */
    static List<String> fitToOneLine(List<String> skills, TechnicalSkillsRegion region, PDFont measuringFont) throws IOException {
        if (skills.isEmpty()) {
            throw new GoldenMasterCvTemplateException("Cannot render an empty Technical Skills list into the golden master template");
        }
        for (int count = skills.size(); count >= 1; count--) {
            List<String> candidate = skills.subList(0, count);
            String text = String.join(SEPARATOR, candidate);
            float width = measuringFont.getStringWidth(text) / 1000f * region.rawFontSize();
            if (width <= region.localAvailableWidth()) {
                return List.copyOf(candidate);
            }
        }
        throw new GoldenMasterCvTemplateException(
                "Not even a single skill fits within the golden master template's Technical Skills region");
    }
}

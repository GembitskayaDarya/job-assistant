package com.darya.jobassistant.integrations.documentrendering.pdfbox.goldenmaster;

import java.util.List;

/**
 * Sprint 11 Golden Master Template Rendering: the geometry {@link TechnicalSkillsRegionLocator}
 * measured for one loaded golden master {@code PDDocument} - everything {@link GoldenMasterCvRenderer}
 * needs to remove the old Technical Skills value token block(s) and splice a replacement into the
 * exact same content-stream position, without recomputing or re-guessing any of it.
 *
 * @param rawFontSize the value row's own raw (pre-CTM, "local") {@code Tf} size - reused verbatim
 *     for the replacement, so the new text is never smaller than the template's own size
 * @param localAvailableWidth the width budget, expressed in the same local/pre-CTM unit space as
 *     {@code rawFontSize} (see {@link TechnicalSkillsRegionLocator}), that replacement text must fit
 *     within on one line
 * @param insertionTx the raw {@code Tm} X translation of the first value row - the replacement's own
 *     {@code Tm} reuses this exactly, since it is spliced back into the same {@code cm}-transformed
 *     token-stream position the removed block occupied (see {@link GoldenMasterCvRenderer})
 * @param insertionTy the raw {@code Tm} Y translation of the first value row - reused exactly, same
 *     reasoning as {@link #insertionTx()}
 * @param tokenRangesToRemove every {@code [btIndex, etIndex]} (inclusive) content-stream token range
 *     that must be deleted - one per matched value row
 */
record TechnicalSkillsRegion(
        float rawFontSize,
        float localAvailableWidth,
        float insertionTx,
        float insertionTy,
        List<int[]> tokenRangesToRemove) {
}

package com.darya.jobassistant.applicationmaterials.render.ats;

/**
 * Sprint 11 Big Block 7: provider-neutral PDF text-extraction boundary used only for ATS structural
 * verification - the read counterpart of {@code ApplicationMaterialDocumentRendererPort#renderCv}.
 * An implementation must be deterministic for identical input bytes and must never write to the
 * filesystem, call a network service, or depend on anything beyond the bytes it is given.
 *
 * @throws AtsTextExtractionException if the given bytes cannot be parsed as a PDF
 */
public interface AtsPdfTextExtractorPort {

    String extractText(byte[] pdfBytes);
}

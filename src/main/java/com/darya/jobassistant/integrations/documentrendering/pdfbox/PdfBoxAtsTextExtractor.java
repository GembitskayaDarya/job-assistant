package com.darya.jobassistant.integrations.documentrendering.pdfbox;

import com.darya.jobassistant.applicationmaterials.render.ats.AtsPdfTextExtractorPort;
import com.darya.jobassistant.applicationmaterials.render.ats.AtsTextExtractionException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Sprint 11 Big Block 7: the only layer that knows ATS text-extraction verification currently uses
 * Apache PDFBox's own {@link PDFTextStripper} - implements {@link AtsPdfTextExtractorPort}. {@code
 * setSortByPosition(true)} makes extraction order deterministic and reading-order-faithful (top to
 * bottom, left to right per line) rather than PDF content-stream operator order, which is what {@code
 * AtsCvVerifier}'s ordering checks require to be meaningful.
 */
@Component
public class PdfBoxAtsTextExtractor implements AtsPdfTextExtractorPort {

    @Override
    public String extractText(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (IOException | RuntimeException e) {
            throw new AtsTextExtractionException("Failed to extract text from rendered CV PDF for ATS verification", e);
        }
    }
}

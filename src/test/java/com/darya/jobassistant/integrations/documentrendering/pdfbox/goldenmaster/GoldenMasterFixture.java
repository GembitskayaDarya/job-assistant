package com.darya.jobassistant.integrations.documentrendering.pdfbox.goldenmaster;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.util.Matrix;

/**
 * Test-only, synthetic, deliberately fake ("JOHN DOE" / "EXAMPLE CORP") stand-in for the real,
 * private golden master CV PDF - built programmatically so no real personal data ever needs to be
 * committed to the repository as a binary test fixture. Also the shared template every full
 * end-to-end Spring context test uses (wired via {@code CV_GOLDEN_MASTER_TEMPLATE_PATH} in {@code
 * build.gradle}), so its fake header/company/mentoring/language content is deliberately chosen to
 * match what those tests' own candidate/baseline fixtures assemble - see {@code
 * PrepareApplicationPackageUseCaseConcurrencyIntegrationTest}/{@code
 * RenderApplicationMaterialsUseCaseIntegrationTest} for the other half of that alignment.
 *
 * <p>Structurally faithful to what {@link TechnicalSkillsRegionLocator}/{@link GoldenMasterCvRenderer}
 * actually assume about the real template (a {@code cm} scale established once before the first
 * {@code BT}, then one self-contained, absolutely-positioned {@code BT...Tm...Tf...Tj...ET} block per
 * visual row - see those classes' javadoc) without reproducing its exact layout: this fixture
 * deliberately uses a different scale (0.8, not the real template's 0.75) and different row
 * positions/fonts, specifically to prove the production code never hardcodes either.
 */
final class GoldenMasterFixture {

    static final String ORIGINAL_SKILLS_TEXT = "Java | Spring | Docker";

    private GoldenMasterFixture() {
    }

    private static final float LOCAL_X = 60f;

    static byte[] build() throws IOException {
        return build(true);
    }

    /** {@code includeLanguagesHeading=false} produces a deliberately broken fixture - missing one required section heading - to exercise {@link GoldenMasterCvTemplate}'s integrity-check failure path. */
    static byte[] buildMissingLanguagesHeading() throws IOException {
        return build(false);
    }

    private static byte[] build(boolean includeLanguagesHeading) throws IOException {
        try (PDDocument document = new PDDocument()) {
            // Pinned so two build() calls produce byte-identical output - PDFBox otherwise
            // generates a fresh, effectively random trailer /ID on every save() of a brand-new
            // PDDocument, which would make this fixture non-deterministic for no reason.
            document.setDocumentId(1L);
            PDType0Font font = loadFont(document);

            PDPage page1 = new PDPage(PDRectangle.A4);
            document.addPage(page1);
            float pageHeight = PDRectangle.A4.getHeight();

            try (PDPageContentStream cs = new PDPageContentStream(document, page1)) {
                cs.transform(new Matrix(1, 0, 0, -1, 0, pageHeight));
                cs.saveGraphicsState();
                cs.transform(new Matrix(0.8f, 0, 0, 0.8f, 0, 0));

                drawRow(cs, font, "JOHN DOE", LOCAL_X, 100, 20);
                drawRow(cs, font, "PROFESSIONAL SUMMARY", LOCAL_X, 140, 14);
                drawRow(cs, font, "Backend engineer with experience.", LOCAL_X, 170, 12);
                drawRow(cs, font, "TECHNICAL SKILLS", LOCAL_X, 210, 14);
                drawRow(cs, font, ORIGINAL_SKILLS_TEXT, LOCAL_X, 240, 12);
                drawRow(cs, font, "PROFESSIONAL EXPERIENCE", LOCAL_X, 280, 14);
                // Uppercase - matches the real golden master's own convention (company/mentoring-
                // org names are drawn uppercase, see PdfBoxApplicationMaterialDocumentRenderer's
                // former "Golden Master CV Lock" javadoc) and AtsCvVerifier's toUppercaseHeading
                // comparison for these two fields specifically.
                drawRow(cs, font, "EXAMPLE CORP", LOCAL_X, 310, 12);
                drawRow(cs, font, "MENTORING EXPERIENCE", LOCAL_X, 350, 14);
                drawRow(cs, font, "EXAMPLE MENTORING ORG", LOCAL_X, 380, 12);
                drawRow(cs, font, "PERSONAL PROJECT", LOCAL_X, 420, 14);
                drawRow(cs, font, "Example Project", LOCAL_X, 450, 12);
                drawRow(cs, font, "EDUCATION", LOCAL_X, 490, 14);
                drawRow(cs, font, "Example University", LOCAL_X, 520, 12);
                if (includeLanguagesHeading) {
                    drawRow(cs, font, "LANGUAGES", LOCAL_X, 560, 14);
                    // Matches candidate-profile-test.yml's two seeded languages, since this
                    // fixture is also loaded (via CV_GOLDEN_MASTER_TEMPLATE_PATH, build.gradle)
                    // by full end-to-end integration tests that assemble a real TailoredCvDocument
                    // from that same test candidate profile and run it through AtsCvVerifier.
                    drawRow(cs, font, "English: Fluent", LOCAL_X, 590, 12);
                    drawRow(cs, font, "Polish: Conversational", LOCAL_X, 615, 12);
                }

                cs.restoreGraphicsState();
            }

            PDPage page2 = new PDPage(PDRectangle.A4);
            document.addPage(page2);
            try (PDPageContentStream cs = new PDPageContentStream(document, page2)) {
                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(60, 700);
                cs.showText("Page 2 filler content.");
                cs.endText();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static void drawRow(PDPageContentStream cs, PDType0Font font, String text, float tx, float ty, float fontSize) throws IOException {
        cs.beginText();
        cs.setTextMatrix(new Matrix(1, 0, 0, -1, tx, ty));
        cs.setFont(font, fontSize);
        cs.showText(text);
        cs.endText();
    }

    private static PDType0Font loadFont(PDDocument document) throws IOException {
        try (InputStream fontStream = GoldenMasterFixture.class.getClassLoader().getResourceAsStream("fonts/ttf/Inter/Inter-Regular.ttf")) {
            return PDType0Font.load(document, fontStream, true);
        }
    }
}

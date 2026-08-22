package com.darya.jobassistant.integrations.documentrendering.pdfbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.junit.jupiter.api.Test;

/**
 * Sprint 11 Final Application Package Quality Hardening (Part 11): deterministic layout-invariant
 * tests for {@link PdfPageCursor}'s low-level draw primitives - the exact class the earlier Oracle
 * Database bug and this block's rule/spacing regressions both traced back to. Verifies the primitives
 * themselves advance the vertical cursor by exactly the amount they claim to, independent of any CV/
 * cover-letter content, so a future content change can never silently reintroduce a spacing defect by
 * relying on an inaccurate primitive.
 */
class PdfPageCursorTest {

    private static final float MARGIN = 50f;
    private static final float FONT_SIZE = 10.5f;
    private static final float LEADING = 13f;

    @Test
    void drawHorizontalRule_advancesCursorByExactlyItsThickness_noHiddenGap() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType0Font font = loadFont(document);
            try (PdfPageCursor cursor = new PdfPageCursor(document, PDRectangle.A4, MARGIN, MARGIN, MARGIN, MARGIN)) {
                float before = cursor.cursorY();
                cursor.drawHorizontalRule(0.75f);
                assertThat(cursor.cursorY()).isEqualTo(before - 0.75f);
            }
        }
    }

    @Test
    void writeLine_advancesCursorByExactlyTheGivenLeading() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType0Font font = loadFont(document);
            try (PdfPageCursor cursor = new PdfPageCursor(document, PDRectangle.A4, MARGIN, MARGIN, MARGIN, MARGIN)) {
                float before = cursor.cursorY();
                cursor.writeLine("Section Heading", font, FONT_SIZE, LEADING, 0);
                assertThat(cursor.cursorY()).isEqualTo(before - LEADING);
            }
        }
    }

    @Test
    void headingThenGapThenRuleThenGap_cursorPositionsNeverOverlap() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType0Font font = loadFont(document);
            try (PdfPageCursor cursor = new PdfPageCursor(document, PDRectangle.A4, MARGIN, MARGIN, MARGIN, MARGIN)) {
                float headingBaseline = cursor.cursorY();
                cursor.writeLine("Technical Skills", font, 13f, 16f, 0);
                float afterHeading = cursor.cursorY();
                assertThat(afterHeading).isLessThan(headingBaseline);

                cursor.addSpacing(4f);
                float beforeRule = cursor.cursorY();
                assertThat(beforeRule).isLessThan(afterHeading);

                cursor.drawHorizontalRule(0.75f);
                float afterRule = cursor.cursorY();
                assertThat(afterRule).isEqualTo(beforeRule - 0.75f);

                cursor.addSpacing(8f);
                float contentBaseline = cursor.cursorY();
                assertThat(contentBaseline).isLessThan(afterRule);

                // The rule's own y-position must sit strictly between the heading's baseline and the
                // following content's baseline - never coincident with (touching) either.
                assertThat(beforeRule).isLessThan(headingBaseline).isGreaterThan(contentBaseline);
            }
        }
    }

    @Test
    void writeWrappedList_multipleWrappedLines_advancesYByExactlyLeadingTimesLineCount() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType0Font font = loadFont(document);
            try (PdfPageCursor cursor = new PdfPageCursor(document, PDRectangle.A4, MARGIN, MARGIN, MARGIN, MARGIN)) {
                List<String> longSkillList = List.of("Java", "Spring Boot", "Spring Framework", "REST API", "Spring Data JPA",
                        "Apache Kafka", "Oracle Database", "Maven", "OpenAI API", "CI/CD", "PostgreSQL", "Docker", "Kubernetes");
                float before = cursor.cursorY();
                cursor.writeWrappedList(longSkillList, " | ", font, FONT_SIZE, LEADING, 0);
                float after = cursor.cursorY();
                float consumed = before - after;

                assertThat(consumed).isGreaterThan(LEADING).as("a long skill list must wrap onto more than one line");
                assertThat(consumed % LEADING).isCloseTo(0f, org.assertj.core.data.Offset.offset(0.01f));
            }
        }
    }

    @Test
    void longTechnologyList_correctlyAdvancesY_matchingLineCount() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType0Font font = loadFont(document);
            try (PdfPageCursor cursor = new PdfPageCursor(document, PDRectangle.A4, MARGIN, MARGIN, MARGIN, MARGIN)) {
                List<String> manyTechnologies = List.of("Java 25", "Spring Boot", "Kafka", "Redis", "REST API", "PostgreSQL",
                        "Git", "Gradle", "AWS Keyspaces", "Swagger", "RestTemplate", "Apache HttpClient", "AWS MSK", "AWS IAM",
                        "AWS Glue Schema Registry", "Avro", "Hibernate/JPA", "Liquibase", "Docker", "Kubernetes");
                float before = cursor.cursorY();
                cursor.writeWrappedList(manyTechnologies, ", ", font, FONT_SIZE, LEADING, 24f);
                float after = cursor.cursorY();
                float consumed = before - after;

                assertThat(consumed).isGreaterThan(LEADING * 2).as("20 technologies must wrap onto several lines");
                assertThat(consumed % LEADING).isCloseTo(0f, org.assertj.core.data.Offset.offset(0.01f));
            }
        }
    }

    @Test
    void ensureRoomFor_insufficientSpace_startsNewPageWithFullTopMargin() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType0Font font = loadFont(document);
            try (PdfPageCursor cursor = new PdfPageCursor(document, PDRectangle.A4, MARGIN, MARGIN, MARGIN, MARGIN)) {
                // Drain most of the page so only a small amount of room remains.
                while (cursor.hasRoomFor(60f)) {
                    cursor.writeLine("filler", font, FONT_SIZE, LEADING, 0);
                }
                assertThat(cursor.hasRoomFor(60f)).isFalse();

                cursor.ensureRoomFor(60f);

                assertThat(cursor.hasRoomFor(60f)).isTrue();
                assertThat(cursor.cursorY()).isEqualTo(cursor.pageHeight() - cursor.topMargin());
                assertThat(document.getNumberOfPages()).isEqualTo(2);
            }
        }
    }

    @Test
    void ensureRoomFor_sufficientSpace_doesNotStartANewPage() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType0Font font = loadFont(document);
            try (PdfPageCursor cursor = new PdfPageCursor(document, PDRectangle.A4, MARGIN, MARGIN, MARGIN, MARGIN)) {
                float before = cursor.cursorY();
                cursor.ensureRoomFor(60f);
                assertThat(cursor.cursorY()).isEqualTo(before);
                assertThat(document.getNumberOfPages()).isEqualTo(1);
            }
        }
    }

    @Test
    void freshPage_startsWithPositiveTopMargin() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType0Font font = loadFont(document);
            try (PdfPageCursor cursor = new PdfPageCursor(document, PDRectangle.A4, MARGIN, MARGIN, MARGIN, MARGIN)) {
                assertThat(cursor.cursorY()).isEqualTo(cursor.pageHeight() - cursor.topMargin());
                assertThat(cursor.topMargin()).isGreaterThan(0f);
            }
        }
    }

    @Test
    void hasRoomFor_reflectsCurrentBottomMargin() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType0Font font = loadFont(document);
            try (PdfPageCursor cursor = new PdfPageCursor(document, PDRectangle.A4, MARGIN, MARGIN, MARGIN, MARGIN)) {
                float roomAboveMargin = cursor.cursorY() - cursor.bottomMargin();
                assertThat(cursor.hasRoomFor(roomAboveMargin - 1f)).isTrue();
                assertThat(cursor.hasRoomFor(roomAboveMargin + 1f)).isFalse();
            }
        }
    }

    private PDType0Font loadFont(PDDocument document) throws IOException {
        try (InputStream fontStream = getClass().getClassLoader().getResourceAsStream("fonts/ttf/Inter/Inter-Regular.ttf")) {
            return PDType0Font.load(document, fontStream, true);
        }
    }
}

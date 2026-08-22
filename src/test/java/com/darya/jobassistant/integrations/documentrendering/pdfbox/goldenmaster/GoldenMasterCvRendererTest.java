package com.darya.jobassistant.integrations.documentrendering.pdfbox.goldenmaster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/**
 * Sprint 11 Golden Master Template Rendering: proves the content-stream surgery itself against
 * {@link GoldenMasterFixture} - a synthetic, structurally-faithful, non-private stand-in for the real
 * golden master (see that class's javadoc for exactly what it does and does not reproduce).
 * Gated tests against the real, private template file live in {@link GoldenMasterCvTemplateRealFileTest}.
 */
class GoldenMasterCvRendererTest {

    @Test
    void render_genuinelyRemovesOldSkillsText_fromTheExtractedPdfText() throws IOException {
        byte[] result = renderWithSkills(List.of("Kotlin", "Kubernetes", "Terraform"));

        String text = extractText(result);
        assertThat(text).doesNotContain(GoldenMasterFixture.ORIGINAL_SKILLS_TEXT);
        assertThat(text).doesNotContain("Java | Spring");
        assertThat(text).contains("Kotlin | Kubernetes | Terraform");
    }

    @Test
    void render_preservesEveryOtherFixtureSection_untouched() throws IOException {
        byte[] result = renderWithSkills(List.of("Kotlin", "Kubernetes"));

        String text = extractText(result);
        assertThat(text).contains("JOHN DOE");
        assertThat(text).contains("PROFESSIONAL SUMMARY");
        assertThat(text).contains("Backend engineer with experience.");
        assertThat(text).contains("PROFESSIONAL EXPERIENCE");
        assertThat(text).contains("EXAMPLE CORP");
        assertThat(text).contains("MENTORING EXPERIENCE");
        assertThat(text).contains("EXAMPLE MENTORING ORG");
        assertThat(text).contains("PERSONAL PROJECT");
        assertThat(text).contains("Example Project");
        assertThat(text).contains("EDUCATION");
        assertThat(text).contains("Example University");
        assertThat(text).contains("LANGUAGES");
        assertThat(text).contains("English: Fluent");
        assertThat(text).contains("Polish: Conversational");
        assertThat(text).contains("Page 2 filler content.");

        try (PDDocument pdf = Loader.loadPDF(result)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(2);
        }
    }

    @Test
    void render_sameInput_producesByteIdenticalOutput() throws IOException {
        List<String> skills = List.of("Kotlin", "Kubernetes", "Terraform");
        byte[] first = renderWithSkills(skills);
        byte[] second = renderWithSkills(skills);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void render_skillsListTooWideForOneLine_dropsTrailingLowestPrioritySkills_toFit() throws IOException {
        List<String> manySkills = IntStream.range(0, 30)
                .mapToObj(i -> "VeryLongSkillNameNumber" + i)
                .toList();

        GoldenMasterCvRenderer.Result result = renderResultWithSkills(manySkills);

        assertThat(result.renderedSkills()).isNotEmpty();
        assertThat(result.renderedSkills().size()).isLessThan(manySkills.size());
        assertThat(manySkills).startsWith(result.renderedSkills().toArray(new String[0]));

        String text = extractText(result.pdfBytes());
        assertThat(text).doesNotContain(manySkills.get(manySkills.size() - 1));
    }

    @Test
    void render_fullSkillsListAlreadyFits_isReturnedUnchanged() throws IOException {
        List<String> skills = List.of("Kotlin", "Kubernetes");

        GoldenMasterCvRenderer.Result result = renderResultWithSkills(skills);

        assertThat(result.renderedSkills()).isEqualTo(skills);
    }

    @Test
    void render_emptySkills_failsLoudly_ratherThanRenderingAnEmptyLine() {
        assertThatThrownBy(() -> renderWithSkills(List.of()))
                .isInstanceOf(GoldenMasterCvTemplateException.class);
    }

    private byte[] renderWithSkills(List<String> skills) throws IOException {
        return renderResultWithSkills(skills).pdfBytes();
    }

    private GoldenMasterCvRenderer.Result renderResultWithSkills(List<String> skills) throws IOException {
        byte[] fixtureBytes = GoldenMasterFixture.build();
        try (PDDocument document = Loader.loadPDF(fixtureBytes)) {
            return GoldenMasterCvRenderer.render(document, skills);
        }
    }

    private String extractText(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return new PDFTextStripper().getText(document);
        }
    }
}

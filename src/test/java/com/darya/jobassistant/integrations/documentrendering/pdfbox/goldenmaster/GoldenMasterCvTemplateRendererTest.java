package com.darya.jobassistant.integrations.documentrendering.pdfbox.goldenmaster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.applicationmaterials.render.model.DocumentRenderingException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Sprint 11 Golden Master Template Rendering: the public facade {@code PdfBoxApplicationMaterialDocumentRenderer} calls - proves it delegates correctly and never lets a raw PDFBox/internal exception escape. */
class GoldenMasterCvTemplateRendererTest {

    @TempDir
    Path tempDir;

    @Test
    void render_delegatesToTheGoldenMasterTemplate_andReturnsTheFittedSkills() throws IOException {
        GoldenMasterCvTemplateRenderer renderer = new GoldenMasterCvTemplateRenderer(validTemplate());

        GoldenMasterCvTemplateRenderer.Result result = renderer.render(List.of("Kotlin", "Kubernetes"));

        assertThat(result.pdfBytes()).isNotEmpty();
        assertThat(result.renderedSkills()).isEqualTo(List.of("Kotlin", "Kubernetes"));
    }

    @Test
    void render_emptySkills_wrapsFailureAsDocumentRenderingException_neverALowerLevelException() throws IOException {
        GoldenMasterCvTemplateRenderer renderer = new GoldenMasterCvTemplateRenderer(validTemplate());

        assertThatThrownBy(() -> renderer.render(List.of()))
                .isInstanceOf(DocumentRenderingException.class)
                .hasCauseInstanceOf(GoldenMasterCvTemplateException.class);
    }

    private GoldenMasterCvTemplate validTemplate() throws IOException {
        Path path = tempDir.resolve("golden-master-fixture.pdf");
        Files.write(path, GoldenMasterFixture.build());
        return new GoldenMasterCvTemplate(new GoldenMasterCvTemplateProperties(path.toString()));
    }
}

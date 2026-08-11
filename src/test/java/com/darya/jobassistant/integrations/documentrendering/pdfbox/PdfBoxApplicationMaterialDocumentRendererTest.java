package com.darya.jobassistant.integrations.documentrendering.pdfbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCvSkill;
import com.darya.jobassistant.applicationmaterials.render.model.RenderableCoverLetter;
import com.darya.jobassistant.applicationmaterials.render.model.RenderableCv;
import com.darya.jobassistant.applicationmaterials.render.model.RenderableCvExperience;
import com.darya.jobassistant.applicationmaterials.render.model.RenderableCvProject;
import com.darya.jobassistant.applicationmaterials.render.model.RenderedDocument;
import com.darya.jobassistant.candidates.SkillProficiency;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class PdfBoxApplicationMaterialDocumentRendererTest {

    private final PdfBoxApplicationMaterialDocumentRenderer renderer = new PdfBoxApplicationMaterialDocumentRenderer();

    // ==================== 9. CV PDF generation ====================

    @Test
    void renderCv_producesValidPdfWithExtractableText() throws IOException {
        RenderableCv cv = validCv();

        RenderedDocument document = renderer.renderCv(cv);

        assertThat(document.contentType()).isEqualTo("application/pdf");
        String text = extractText(document.content());
        assertThat(text).contains("Senior Java Backend Engineer");
        assertThat(text).contains("Tailored Backend Engineer");
        assertThat(text).contains("Built resilient backend services");
        assertThat(text).contains("Acme Corp");
        assertThat(text).contains("Billing Platform");
        assertThat(text).contains("Java");
        assertThat(text).contains("English");
    }

    // ==================== 10. Cover letter PDF generation ====================

    @Test
    void renderCoverLetter_producesValidPdfWithExtractableText() throws IOException {
        RenderableCoverLetter coverLetter = new RenderableCoverLetter(
                "Dear Hiring Manager,",
                List.of("I am excited to apply for this role.", "My experience aligns well with your needs."),
                "Sincerely, the candidate",
                "Senior Backend Engineer", "Acme Corp");

        RenderedDocument document = renderer.renderCoverLetter(coverLetter);

        String text = extractText(document.content());
        assertThat(text).contains("Re: Senior Backend Engineer at Acme Corp");
        assertThat(text).contains("Dear Hiring Manager");
        assertThat(text).contains("I am excited to apply");
        assertThat(text).contains("Sincerely, the candidate");
    }

    // ==================== 12. Long content -> valid multi-page PDF ====================

    @Test
    void renderCv_longContent_producesMultiplePages() throws IOException {
        List<String> manyBullets = java.util.stream.IntStream.range(0, 60)
                .mapToObj(i -> "Delivered measurable impact on backend reliability and performance in project " + i)
                .toList();
        RenderableCvExperience longExperience = new RenderableCvExperience(
                "Acme Corp", "Senior Backend Engineer", "Full-time", "Remote", "Remote",
                LocalDate.of(2015, 1, 1), null, true, manyBullets, List.of());
        RenderableCv cv = new RenderableCv(
                "Senior Java Backend Engineer", "Senior", 10, "Tailored Backend Engineer", "Experienced engineer.",
                List.of(new GeneratedCvSkill("Java", SkillProficiency.EXPERT)), List.of(longExperience), List.of("English"));

        RenderedDocument document = renderer.renderCv(cv);

        try (PDDocument pdf = Loader.loadPDF(document.content())) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
        }
        String text = extractText(document.content());
        assertThat(text).contains("project 0");
        assertThat(text).contains("project 59");
    }

    // ==================== Determinism (idempotent same-content storage relies on this) ====================

    @Test
    void renderCv_sameInput_producesByteIdenticalOutput() {
        RenderableCv cv = validCv();

        RenderedDocument first = renderer.renderCv(cv);
        RenderedDocument second = renderer.renderCv(cv);

        assertThat(first.content()).isEqualTo(second.content());
    }

    @Test
    void renderCoverLetter_sameInput_producesByteIdenticalOutput() {
        RenderableCoverLetter coverLetter = new RenderableCoverLetter(
                null, List.of("Paragraph one.", "Paragraph two."), "Regards", "Engineer", "Acme");

        RenderedDocument first = renderer.renderCoverLetter(coverLetter);
        RenderedDocument second = renderer.renderCoverLetter(coverLetter);

        assertThat(first.content()).isEqualTo(second.content());
    }

    // ==================== Polish diacritics and Cyrillic are preserved exactly, never transliterated ====================

    @Test
    void renderCv_polishDiacritics_arePreservedExactlyNotTransliterated() throws IOException {
        RenderableCvExperience experience = new RenderableCvExperience(
                "Zaklad Produkcyjny w Łodzi", "Starszy Programista", null, null, null,
                LocalDate.of(2018, 1, 1), LocalDate.of(2020, 1, 1), false,
                List.of("Zażółć gęślą jaźń"), List.of());
        RenderableCv cv = new RenderableCv(
                "Starszy Inżynier Java", "Starszy", 8, "Doświadczony inżynier backendu w Gdańsku", "Buduje solidne systemy.",
                List.of(), List.of(experience), List.of("polski"));

        RenderedDocument document = renderer.renderCv(cv);

        assertThat(document.content()).isNotEmpty();
        String text = extractText(document.content());
        assertThat(text).contains("Łodzi");
        assertThat(text).contains("Gdańsku");
        assertThat(text).contains("Zażółć gęślą jaźń");
        assertThat(text).contains("Inżynier");
        assertThat(text).doesNotContain("?");
    }

    @Test
    void renderCv_cyrillicText_isPreservedExactlyNotTransliterated() throws IOException {
        RenderableCvExperience experience = new RenderableCvExperience(
                "ООО Ромашка", "Старший программист", null, null, null,
                LocalDate.of(2018, 1, 1), LocalDate.of(2020, 1, 1), false,
                List.of("Отвечал за разработку и поддержку платёжных систем"), List.of());
        RenderableCv cv = new RenderableCv(
                "Старший инженер Java", "Старший", 8, "Опытный инженер бэкенда", "Разрабатывает надёжные системы.",
                List.of(), List.of(experience), List.of("русский"));

        RenderedDocument document = renderer.renderCv(cv);

        assertThat(document.content()).isNotEmpty();
        String text = extractText(document.content());
        assertThat(text).contains("ООО Ромашка");
        assertThat(text).contains("Отвечал за разработку и поддержку платёжных систем");
        assertThat(text).contains("Опытный инженер бэкенда");
        assertThat(text).doesNotContain("?");
    }

    @Test
    void renderCoverLetter_polishAndCyrillicText_isPreservedExactly() throws IOException {
        RenderableCoverLetter coverLetter = new RenderableCoverLetter(
                "Szanowni Państwo,",
                List.of("Zażółć gęślą jaźń.", "Готов приступить к работе немедленно."),
                "Z poważaniem",
                "Starszy Inżynier", "Łódzka Firma");

        RenderedDocument document = renderer.renderCoverLetter(coverLetter);

        String text = extractText(document.content());
        assertThat(text).contains("Szanowni Państwo");
        assertThat(text).contains("Zażółć gęślą jaźń");
        assertThat(text).contains("Готов приступить к работе немедленно");
        assertThat(text).contains("Z poważaniem");
        assertThat(text).contains("Łódzka Firma");
    }

    // ==================== Helpers ====================

    private RenderableCv validCv() {
        RenderableCvProject project = new RenderableCvProject("Billing Platform", LocalDate.of(2021, 1, 1), LocalDate.of(2022, 1, 1));
        RenderableCvExperience experience = new RenderableCvExperience(
                "Acme Corp", "Senior Backend Engineer", "Full-time", "Remote", "Remote",
                LocalDate.of(2020, 1, 1), null, true,
                List.of("Built resilient backend services", "Led migration to event-driven architecture"),
                List.of(project));
        return new RenderableCv(
                "Senior Java Backend Engineer", "Senior", 6, "Tailored Backend Engineer", "Experienced backend engineer.",
                List.of(new GeneratedCvSkill("Java", SkillProficiency.EXPERT), new GeneratedCvSkill("Kafka", null)),
                List.of(experience), List.of("English"));
    }

    private String extractText(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return new PDFTextStripper().getText(document);
        }
    }
}

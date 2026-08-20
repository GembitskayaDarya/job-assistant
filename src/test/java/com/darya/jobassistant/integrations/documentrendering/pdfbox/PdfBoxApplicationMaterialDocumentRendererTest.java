package com.darya.jobassistant.integrations.documentrendering.pdfbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.applicationmaterials.render.model.RenderableCoverLetter;
import com.darya.jobassistant.applicationmaterials.render.model.RenderedDocument;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvCompany;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvDocument;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvHeader;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvPersonalProject;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvPosition;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvProject;
import com.darya.jobassistant.candidates.CandidateEducationFacts;
import com.darya.jobassistant.candidates.CandidateLanguageFacts;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/**
 * Sprint 11 Big Block 7: proves the ATS-first single-column {@link
 * PdfBoxApplicationMaterialDocumentRenderer#renderCv} layout - reading order, linear role/date text,
 * hyperlinks, Unicode, determinism, and structural PDF properties (not encrypted, searchable text,
 * reasonable page count). {@link #renderCoverLetter_producesValidPdfWithExtractableText} keeps the
 * pre-existing cover-letter coverage, unaffected by this block's CV-side changes.
 */
class PdfBoxApplicationMaterialDocumentRendererTest {

    private final PdfBoxApplicationMaterialDocumentRenderer renderer = new PdfBoxApplicationMaterialDocumentRenderer();

    // ==================== CV PDF generation: extractable text, single-column reading order ====================

    @Test
    void renderCv_producesValidPdfWithExtractableText() throws IOException {
        RenderedDocument document = renderer.renderCv(validCv());

        assertThat(document.contentType()).isEqualTo("application/pdf");
        String text = extractText(document.content());
        assertThat(text).contains("Jane Candidate");
        assertThat(text).contains("Senior Java Backend Engineer");
        assertThat(text).contains("Built resilient backend services");
        assertThat(text).contains("Acme Corp");
        assertThat(text).contains("Billing Platform");
        assertThat(text).contains("Java");
        assertThat(text).contains("English");
    }

    @Test
    void renderCv_singleColumnReadingOrder_headerThenSummaryThenSkillsThenExperienceThenEducationThenLanguages() throws IOException {
        String text = extractText(renderer.renderCv(validCv()).content());

        int nameIndex = text.indexOf("Jane Candidate");
        int summaryIndex = text.indexOf("Professional Summary");
        int skillsIndex = text.indexOf("Technical Skills");
        int experienceIndex = text.indexOf("Professional Experience");
        int educationIndex = text.indexOf("Education");
        int languagesIndex = text.indexOf("Languages");

        assertThat(nameIndex).isLessThan(summaryIndex);
        assertThat(summaryIndex).isLessThan(skillsIndex);
        assertThat(skillsIndex).isLessThan(experienceIndex);
        assertThat(experienceIndex).isLessThan(educationIndex);
        assertThat(educationIndex).isLessThan(languagesIndex);
    }

    @Test
    void renderCv_roleAndDateRenderAsOneLinearTextLine_neverSeparateColumns() throws IOException {
        String text = extractText(renderer.renderCv(validCv()).content());

        // "Title | MMM yyyy - Present" must appear as one contiguous string - proves linear text,
        // not a table/column layout that would put the date somewhere else in the extraction order.
        assertThat(text).contains("Component Lead | Feb 2026 - Present");
    }

    @Test
    void renderCv_companiesOrderedReverseChronologically_mostRecentFirst() throws IOException {
        String text = extractText(renderer.renderCv(validCv()).content());

        int recentCompanyIndex = text.indexOf("Acme Corp");
        int olderCompanyIndex = text.indexOf("Legacy Systems Inc");
        assertThat(recentCompanyIndex).isGreaterThanOrEqualTo(0);
        assertThat(olderCompanyIndex).isGreaterThan(recentCompanyIndex);
    }

    // ==================== Cover letter PDF generation ====================

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

    // ==================== Long content -> valid multi-page, but reasonable, PDF ====================

    @Test
    void renderCv_longContent_producesMultiplePagesButStaysReasonable() throws IOException {
        List<String> manyBullets = java.util.stream.IntStream.range(0, 60)
                .mapToObj(i -> "Delivered measurable impact on backend reliability and performance in project " + i)
                .toList();
        TailoredCvPosition longPosition = new TailoredCvPosition(
                "Senior Backend Engineer", "Full-time", "Remote", "Remote",
                LocalDate.of(2015, 1, 1), null, true, null, manyBullets, List.of(), List.of());
        TailoredCvDocument cv = new TailoredCvDocument(
                new TailoredCvHeader("Jane Candidate", "Senior Java Backend Engineer", "Remote", "jane@example.test", null, null),
                "Experienced engineer.", List.of("Java"),
                List.of(new TailoredCvCompany("Acme Corp", null, null, null, null, List.of(longPosition))),
                List.of(), List.of(), List.of());

        RenderedDocument document = renderer.renderCv(cv);

        try (PDDocument pdf = Loader.loadPDF(document.content())) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            assertThat(pdf.isEncrypted()).isFalse();
        }
        String text = extractText(document.content());
        assertThat(text).contains("project 0");
        assertThat(text).contains("project 59");
    }

    @Test
    void renderCv_realisticMultiCompanyFixture_producesReasonablePageCount() throws IOException {
        RenderedDocument document = renderer.renderCv(validCv());

        try (PDDocument pdf = Loader.loadPDF(document.content())) {
            assertThat(pdf.getNumberOfPages()).isLessThanOrEqualTo(3);
            assertThat(pdf.isEncrypted()).isFalse();
        }
    }

    // ==================== Determinism (idempotent same-content storage relies on this) ====================

    @Test
    void renderCv_sameInput_producesByteIdenticalOutput() {
        TailoredCvDocument cv = validCv();

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

    // ==================== Hyperlinks ====================

    @Test
    void renderCv_linkedinLine_hasClickableHyperlinkAnnotationPointingAtFullUrl() throws IOException {
        RenderedDocument document = renderer.renderCv(validCv());

        try (PDDocument pdf = Loader.loadPDF(document.content())) {
            PDPage firstPage = pdf.getPage(0);
            PDActionURI linkedinAction = findLinkAction(firstPage, "https://www.linkedin.com/in/janecandidate");
            assertThat(linkedinAction).isNotNull();
        }
        String text = extractText(document.content());
        assertThat(text).contains("linkedin.com/in/janecandidate");
        assertThat(text).doesNotContain("https://www.linkedin.com/in/janecandidate");
    }

    @Test
    void renderCv_personalProjectUrl_isVisibleAndClickable() throws IOException {
        RenderedDocument document = renderer.renderCv(validCv());

        try (PDDocument pdf = Loader.loadPDF(document.content())) {
            boolean foundGithubLink = false;
            for (PDPage page : pdf.getPages()) {
                if (findLinkAction(page, "https://github.com/janecandidate/side-project") != null) {
                    foundGithubLink = true;
                    break;
                }
            }
            assertThat(foundGithubLink).isTrue();
        }
        String text = extractText(document.content());
        assertThat(text).contains("github.com/janecandidate/side-project");
    }

    @Test
    void renderCv_longUrl_doesNotOverflowPage_stillExtractableAndValid() throws IOException {
        TailoredCvPersonalProject longUrlProject = new TailoredCvPersonalProject(
                "Side Project", "A hobby project", "https://github.com/some-very-long-organization-name/an-extremely-long-repository-name-example",
                LocalDate.of(2022, 1, 1), null, List.of("Built a thing"), List.of("Java"));
        TailoredCvDocument cv = new TailoredCvDocument(
                new TailoredCvHeader("Jane Candidate", "Senior Java Backend Engineer", null, null, null, null),
                null, List.of(), List.of(), List.of(longUrlProject), List.of(), List.of());

        RenderedDocument document = renderer.renderCv(cv);

        try (PDDocument pdf = Loader.loadPDF(document.content())) {
            assertThat(pdf.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
        String text = extractText(document.content());
        assertThat(text).contains("github.com/some-very-long-organization-name");
    }

    // ==================== Polish diacritics and Cyrillic are preserved exactly, never transliterated ====================

    @Test
    void renderCv_polishDiacritics_arePreservedExactlyNotTransliterated() throws IOException {
        TailoredCvPosition position = new TailoredCvPosition(
                "Starszy Programista", null, null, null,
                LocalDate.of(2018, 1, 1), LocalDate.of(2020, 1, 1), false, null,
                List.of("Zażółć gęślą jaźń"), List.of(), List.of());
        TailoredCvCompany company = new TailoredCvCompany("Zaklad Produkcyjny w Łodzi", null, null, null, null, List.of(position));
        TailoredCvDocument cv = new TailoredCvDocument(
                new TailoredCvHeader("Jan Kowalski", "Doświadczony inżynier backendu w Gdańsku", null, null, null, null),
                "Buduje solidne systemy.", List.of(), List.of(company), List.of(), List.of(), List.of());

        RenderedDocument document = renderer.renderCv(cv);

        assertThat(document.content()).isNotEmpty();
        String text = extractText(document.content());
        assertThat(text).contains("Łodzi");
        assertThat(text).contains("Gdańsku");
        assertThat(text).contains("Zażółć gęślą jaźń");
        assertThat(text).contains("inżynier");
        assertThat(text).doesNotContain("?");
    }

    @Test
    void renderCv_cyrillicText_isPreservedExactlyNotTransliterated() throws IOException {
        TailoredCvPosition position = new TailoredCvPosition(
                "Старший программист", null, null, null,
                LocalDate.of(2018, 1, 1), LocalDate.of(2020, 1, 1), false, null,
                List.of("Отвечал за разработку и поддержку платёжных систем"), List.of(), List.of());
        TailoredCvCompany company = new TailoredCvCompany("ООО Ромашка", null, null, null, null, List.of(position));
        TailoredCvDocument cv = new TailoredCvDocument(
                new TailoredCvHeader("Иван Иванов", "Опытный инженер бэкенда", null, null, null, null),
                "Разрабатывает надёжные системы.", List.of(), List.of(company), List.of(), List.of(), List.of());

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

    private TailoredCvDocument validCv() {
        TailoredCvProject project = new TailoredCvProject("Billing Platform", "Payments system", LocalDate.of(2021, 1, 1),
                LocalDate.of(2022, 1, 1), List.of(), List.of(), List.of("Kafka"));
        TailoredCvPosition recentPosition = new TailoredCvPosition(
                "Component Lead", "Full-time", "Remote", "Remote",
                LocalDate.of(2026, 2, 1), null, true, null,
                List.of("Built resilient backend services", "Led migration to event-driven architecture"), List.of(), List.of(project));
        TailoredCvCompany recentCompany = new TailoredCvCompany("Acme Corp", null, null, null, null, List.of(recentPosition));

        TailoredCvPosition olderPosition = new TailoredCvPosition(
                "Software Engineer", null, null, null,
                LocalDate.of(2018, 1, 1), LocalDate.of(2020, 12, 31), false, null,
                List.of("Maintained internal tooling"), List.of(), List.of());
        TailoredCvCompany olderCompany = new TailoredCvCompany("Legacy Systems Inc", null, null, null, null, List.of(olderPosition));

        TailoredCvPersonalProject personalProject = new TailoredCvPersonalProject(
                "Side Project", "A hobby project", "https://github.com/janecandidate/side-project",
                LocalDate.of(2022, 1, 1), null, List.of("Built a dashboard"), List.of("Java"));

        return new TailoredCvDocument(
                new TailoredCvHeader("Jane Candidate", "Senior Java Backend Engineer", "Remote", "jane@example.test",
                        "+1 555 0100", "https://www.linkedin.com/in/janecandidate"),
                "Experienced backend engineer.", List.of("Java", "Kafka"),
                List.of(recentCompany, olderCompany),
                List.of(personalProject),
                List.of(new CandidateEducationFacts(null, "State University", "BSc", "Computer Science", null, null, null, null, 0)),
                List.of(new CandidateLanguageFacts("English", "Native")));
    }

    private String extractText(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private PDActionURI findLinkAction(PDPage page, String uri) throws IOException {
        for (PDAnnotation annotation : page.getAnnotations()) {
            if (annotation instanceof PDAnnotationLink link && link.getAction() instanceof PDActionURI action) {
                if (uri.equals(action.getURI())) {
                    return action;
                }
            }
        }
        return null;
    }
}

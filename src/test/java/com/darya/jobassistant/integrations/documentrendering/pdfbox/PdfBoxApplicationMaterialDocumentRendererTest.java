package com.darya.jobassistant.integrations.documentrendering.pdfbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.applicationmaterials.render.ats.AtsCvVerifier;
import com.darya.jobassistant.applicationmaterials.render.ats.AtsVerificationResult;
import com.darya.jobassistant.applicationmaterials.render.model.RenderableCoverLetter;
import com.darya.jobassistant.applicationmaterials.render.model.RenderedDocument;
import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidatecontext.cv.document.CvAssembler;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvCompany;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvDocument;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvHeader;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvPersonalProject;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvPosition;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvProject;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceCompany;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePosition;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceProject;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceSnapshot;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceTechnology;
import com.darya.jobassistant.candidates.CandidateEducationFacts;
import com.darya.jobassistant.candidates.CandidateLanguageFacts;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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

    // ==================== Skills/technologies wrap at item boundaries, never mid-phrase (production fix) ====================

    /**
     * Production fix: a real production generation had "Oracle Database" (a two-word skill) split by
     * the old word-level greedy wrap exactly between "Oracle" and "Database" - the two halves landed
     * on separate PDF lines, so {@link PDFTextStripper} extracted them joined by a newline rather than
     * the original space, and {@link AtsCvVerifier} correctly reported {@code MISSING_SKILL_TERM} for
     * an exact-match search of "Oracle Database". This list is long enough to force wrapping (proven
     * below via page/line assertions) and deliberately places multi-word skills at the first, a
     * middle, and the last position to prove {@link PdfPageCursor#writeWrappedList} never splits any
     * of them regardless of where in the list a wrap boundary happens to fall.
     */
    @Test
    void renderCv_longSkillsList_wrapsAcrossLinesButPreservesEveryMultiWordSkillIntact() throws IOException {
        List<String> skills = List.of("Java", "Spring Boot", "Spring Framework", "REST API", "Spring Data JPA",
                "Apache Kafka", "Oracle Database", "Maven", "OpenAI API", "CI/CD");
        TailoredCvDocument cv = new TailoredCvDocument(
                new TailoredCvHeader("Jane Candidate", "Senior Java Backend Engineer", "Remote", "jane@example.test", null, null),
                null, skills, List.of(), List.of(), List.of(), List.of());

        String text = extractText(renderer.renderCv(cv).content());

        for (String skill : skills) {
            assertThat(text).as("skill '%s' must survive intact", skill).contains(skill);
        }
        // Confirms this test actually exercises multi-line wrapping rather than trivially fitting on
        // one line - the skills section itself must occupy more than one extracted line.
        int skillsHeadingIndex = text.indexOf("Technical Skills");
        int experienceOrEndIndex = text.length();
        String skillsBlock = text.substring(skillsHeadingIndex, experienceOrEndIndex);
        assertThat(skillsBlock.split("\n", -1).length).as("skills section must wrap onto more than one line").isGreaterThan(2);

        AtsVerificationResult result = AtsCvVerifier.verify(cv, text);
        assertThat(result.readable()).as("violations: %s", result.violations()).isTrue();
    }

    @Test
    void renderCv_longTechnologiesList_wrapsButPreservesEveryMultiWordTechnologyIntact() throws IOException {
        List<String> technologies = List.of("Java 25", "Spring Boot", "Kafka", "Redis", "REST API", "PostgreSQL",
                "AWS Keyspaces", "Apache HttpClient", "AWS Glue Schema Registry", "Cassandra Java Driver");
        TailoredCvProject project = new TailoredCvProject("Core Service", null, LocalDate.of(2023, 2, 1), null,
                List.of(), List.of(), technologies);
        TailoredCvPosition position = new TailoredCvPosition("Software Backend Engineer", null, null, null,
                LocalDate.of(2023, 2, 1), null, true, null, List.of(), List.of(), List.of(project));
        TailoredCvCompany company = new TailoredCvCompany("Spribe", null, null, null, null, List.of(position));
        TailoredCvDocument cv = new TailoredCvDocument(
                new TailoredCvHeader("Jane Candidate", "Senior Java Backend Engineer", "Remote", "jane@example.test", null, null),
                null, List.of(), List.of(company), List.of(), List.of(), List.of());

        String text = extractText(renderer.renderCv(cv).content());

        for (String technology : technologies) {
            assertThat(text).as("technology '%s' must survive intact", technology).contains(technology);
        }
        assertThat(text).contains("Technologies: Java 25");
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

    // ==================== Order preservation (production fix) ====================

    /**
     * Production fix: this renderer previously re-sorted positions into recency order at render time
     * independently of {@code TailoredCvDocument}'s own order, which could disagree with what {@code
     * AtsCvVerifier} (which walks {@code TailoredCvDocument} as given) expected. The renderer must now
     * trust the document's order completely - proven here by feeding it a document deliberately built
     * in chronological (oldest-first) position order (ordering is {@code CvAssembler}'s job, verified
     * separately in {@code CvAssemblerTest}) and confirming the PDF text preserves that exact order,
     * never "fixing" it into recency order.
     */
    @Test
    void renderCv_preservesPositionOrderExactlyAsGiven_performsNoIndependentRecencySorting() throws IOException {
        TailoredCvPosition olderPosition = new TailoredCvPosition("Senior Backend Engineer", null, null, null,
                LocalDate.of(2023, 2, 1), LocalDate.of(2026, 2, 28), false, null, List.of(), List.of(), List.of());
        TailoredCvPosition currentPosition = new TailoredCvPosition("Component Lead", null, null, null,
                LocalDate.of(2026, 2, 1), null, true, null, List.of(), List.of(), List.of());
        // Deliberately chronological (oldest-first) - the "wrong" final order, to prove the renderer
        // itself performs no correction.
        TailoredCvCompany company = new TailoredCvCompany("Spribe", null, null, null, null, List.of(olderPosition, currentPosition));
        TailoredCvDocument cv = new TailoredCvDocument(
                new TailoredCvHeader("Jane Candidate", "Backend Engineer", "Remote", "jane@example.test", "+1 555 0100", null),
                null, List.of(), List.of(company), List.of(), List.of(), List.of());

        String text = extractText(renderer.renderCv(cv).content());

        assertThat(text.indexOf("Senior Backend Engineer")).isLessThan(text.indexOf("Component Lead"));
    }

    /**
     * Full real pipeline for the exact production shape ({@code CvAssembler} -> this real PDFBox
     * renderer -> real PDF text extraction -> {@code AtsCvVerifier}): a candidate promoted within the
     * same "Core Service" project, two positions at one company, persisted in chronological source
     * order. Proves the fix closes the loop end to end - the assembler's canonical order is what both
     * the renderer draws and the verifier checks, so they can never disagree again.
     */
    @Test
    void renderCv_documentFromCvAssemblerWithPromotionWithinSameProject_passesAtsVerification() throws IOException {
        CvSourceProject seniorProject = new CvSourceProject(UUID.randomUUID(), "Core Service", null,
                LocalDate.of(2023, 2, 1), LocalDate.of(2026, 2, 28), List.of(), List.of(),
                List.of(new CvSourceTechnology(UUID.randomUUID(), "Kafka", null)));
        CvSourcePosition seniorPosition = new CvSourcePosition(UUID.randomUUID(), "Senior Backend Engineer",
                null, null, null, LocalDate.of(2023, 2, 1), LocalDate.of(2026, 2, 28), false, null,
                List.of(), List.of(), List.of(seniorProject));
        CvSourceProject leadProject = new CvSourceProject(UUID.randomUUID(), "Core Service", null,
                LocalDate.of(2026, 2, 1), null, List.of(), List.of(),
                List.of(new CvSourceTechnology(UUID.randomUUID(), "Kubernetes", null)));
        CvSourcePosition leadPosition = new CvSourcePosition(UUID.randomUUID(), "Component Lead",
                null, null, null, LocalDate.of(2026, 2, 1), null, true, null, List.of(), List.of(), List.of(leadProject));
        // Source order is oldest-first, same as this bug's real persisted display_order.
        CvSourceCompany company = new CvSourceCompany(UUID.randomUUID(), "Spribe",
                null, null, null, null, List.of(seniorPosition, leadPosition));
        CandidateProfileFacts profile = new CandidateProfileFacts(
                "Backend Engineer", "Senior", List.of(), List.of(), 6,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null),
                "Jane Candidate", null, null, "https://linkedin.test/in/jane", "Remote", "Backend Engineer", List.of());
        CvSourceSnapshot snapshot = new CvSourceSnapshot(profile, CareerHistoryAvailability.AVAILABLE, List.of(company), List.of());

        TailoredCvDocument document = CvAssembler.assembleBaseline(snapshot);
        String text = extractText(renderer.renderCv(document).content());

        AtsVerificationResult result = AtsCvVerifier.verify(document, text);

        assertThat(result.readable()).isTrue();
        assertThat(result.violations()).isEmpty();
        // The rendered PDF itself shows current role first - confirms the assembler's order, not
        // just the verifier's opinion of it.
        assertThat(text.indexOf("Component Lead")).isLessThan(text.indexOf("Senior Backend Engineer"));
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

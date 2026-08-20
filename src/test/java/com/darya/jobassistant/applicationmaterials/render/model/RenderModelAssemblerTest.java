package com.darya.jobassistant.applicationmaterials.render.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetter;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetterParagraph;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Sprint 11 Big Block 7: {@link RenderModelAssembler} now assembles only the cover-letter half of a
 * render snapshot - CV assembly moved entirely to {@code CvAssembler} (already tested from Block 6),
 * since {@code TailoredCvDocument} is handed to the renderer directly with no intermediate resolution
 * step.
 */
class RenderModelAssemblerTest {

    @Test
    void assembleCoverLetter_carriesTrustedVacancyMetadata() {
        GeneratedCoverLetter generated = validCoverLetter();

        RenderableCoverLetter rendered = RenderModelAssembler.assembleCoverLetter(generated, vacancy());

        assertThat(rendered.vacancyTitle()).isEqualTo("Senior Backend Engineer");
        assertThat(rendered.vacancyCompany()).isEqualTo("Acme Corp");
        assertThat(rendered.closing()).isEqualTo("Sincerely, the candidate");
    }

    @Test
    void assembleCoverLetter_resolvesParagraphTextInOrder() {
        GeneratedCoverLetter generated = new GeneratedCoverLetter(null,
                List.of(new GeneratedCoverLetterParagraph("First.", List.of()), new GeneratedCoverLetterParagraph("Second.", List.of())),
                "Sincerely, the candidate");

        RenderableCoverLetter rendered = RenderModelAssembler.assembleCoverLetter(generated, vacancy());

        assertThat(rendered.paragraphs()).containsExactly("First.", "Second.");
    }

    @Test
    void assembleCoverLetter_preservesGreeting() {
        GeneratedCoverLetter generated = new GeneratedCoverLetter(
                "Dear Hiring Manager,", List.of(new GeneratedCoverLetterParagraph("Body.", List.of())), "Sincerely, the candidate");

        RenderableCoverLetter rendered = RenderModelAssembler.assembleCoverLetter(generated, vacancy());

        assertThat(rendered.greeting()).isEqualTo("Dear Hiring Manager,");
    }

    @Test
    void assembleCoverLetter_nullGenerated_isRejected() {
        assertThatThrownBy(() -> RenderModelAssembler.assembleCoverLetter(null, vacancy())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assembleCoverLetter_nullVacancy_isRejected() {
        assertThatThrownBy(() -> RenderModelAssembler.assembleCoverLetter(validCoverLetter(), null)).isInstanceOf(IllegalArgumentException.class);
    }

    private JobOffer vacancy() {
        return new JobOffer("job-1", "Senior Backend Engineer", "Acme Corp", "Remote", null,
                "We need a backend engineer.", "https://example.com/job-1", "test");
    }

    private GeneratedCoverLetter validCoverLetter() {
        return new GeneratedCoverLetter(null, List.of(new GeneratedCoverLetterParagraph("Body text.", List.of())), "Sincerely, the candidate");
    }
}

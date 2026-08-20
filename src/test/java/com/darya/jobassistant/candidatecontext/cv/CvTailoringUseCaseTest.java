package com.darya.jobassistant.candidatecontext.cv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvDocument;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceAchievement;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceCompany;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePosition;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceResponsibility;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceSnapshot;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvAchievementTailoring;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvPositionTailoring;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvResponsibilityTailoring;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvTailoringResult;
import com.darya.jobassistant.candidatecontext.cv.tailoring.ai.CvTailoringAiException;
import com.darya.jobassistant.candidatecontext.cv.tailoring.ai.CvTailoringAiPort;
import com.darya.jobassistant.candidatecontext.cv.tailoring.validation.CvTailoringValidationException;
import com.darya.jobassistant.candidatecontext.cv.tailoring.validation.CvTailoringViolationCategory;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Sprint 11 Big Block 6: end-to-end coverage of the whole content pipeline - {@code
 * Vacancy + CvSourceSnapshot -> fake CvTailoringAiPort -> CvTailoringValidator -> CvAssembler ->
 * TailoredCvDocument} - using a fake {@link CvTailoringAiPort} rather than Spring AI, per this
 * block's test requirements.
 */
class CvTailoringUseCaseTest {

    private static final UUID POSITION_ID = UUID.randomUUID();
    private static final UUID RESPONSIBILITY_ID = UUID.randomUUID();
    private static final UUID ACHIEVEMENT_ID = UUID.randomUUID();
    private static final UUID SKILL_ID = UUID.randomUUID();

    @Test
    void tailor_fullPipeline_validAiOutput_producesAssembledDocument() {
        CvTailoringResult tailoringResult = new CvTailoringResult(
                "Tailored senior backend summary", List.of(SKILL_ID),
                List.of(new CvPositionTailoring(POSITION_ID,
                        List.of(new CvResponsibilityTailoring(RESPONSIBILITY_ID, "Rewritten responsibility")),
                        List.of(new CvAchievementTailoring(ACHIEVEMENT_ID, null)))),
                List.of());
        CvTailoringUseCase useCase = new CvTailoringUseCase(fakePort(tailoringResult));

        TailoredCvDocument document = useCase.tailor(vacancy(), snapshot());

        assertThat(document.professionalSummary()).isEqualTo("Tailored senior backend summary");
        assertThat(document.skills()).containsExactly("Java");
        assertThat(document.experience().get(0).positions().get(0).responsibilities()).containsExactly("Rewritten responsibility");
        assertThat(document.experience().get(0).positions().get(0).achievements()).containsExactly("Owned the on-call rotation");
    }

    @Test
    void tailor_aiPortThrowsCvTailoringAiException_propagatesUnchanged_neverReachesAssembly() {
        CvTailoringAiException providerFailure = new CvTailoringAiException("boom", new RuntimeException("network error"));
        CvTailoringUseCase useCase = new CvTailoringUseCase((vacancy, snapshot) -> {
            throw providerFailure;
        });

        assertThatThrownBy(() -> useCase.tailor(vacancy(), snapshot())).isSameAs(providerFailure);
    }

    @Test
    void tailor_invalidAiOutput_throwsCvTailoringValidationException_neverReachesAssembly() {
        UUID unknownSkill = UUID.randomUUID();
        CvTailoringResult invalidResult = new CvTailoringResult(null, List.of(unknownSkill), List.of(), List.of());
        CvTailoringUseCase useCase = new CvTailoringUseCase(fakePort(invalidResult));

        assertThatThrownBy(() -> useCase.tailor(vacancy(), snapshot()))
                .isInstanceOf(CvTailoringValidationException.class)
                .satisfies(e -> assertThat(((CvTailoringValidationException) e).violations())
                        .extracting(v -> v.category())
                        .containsExactly(CvTailoringViolationCategory.UNKNOWN_SKILL_REFERENCE));
    }

    @Test
    void tailor_unexpectedRuntimeException_propagatesUnmasked() {
        RuntimeException unexpected = new IllegalStateException("unexpected application error");
        CvTailoringUseCase useCase = new CvTailoringUseCase((vacancy, snapshot) -> {
            throw unexpected;
        });

        assertThatThrownBy(() -> useCase.tailor(vacancy(), snapshot())).isSameAs(unexpected);
    }

    // ==================== Fixtures ====================

    private CvTailoringAiPort fakePort(CvTailoringResult result) {
        return (vacancy, snapshot) -> result;
    }

    private JobOffer vacancy() {
        return new JobOffer("job-1", "Backend Engineer", "Acme Corp", "Remote", null,
                "We need a backend engineer.", "https://example.com/job-1", "test");
    }

    private CvSourceSnapshot snapshot() {
        CvSourcePosition position = new CvSourcePosition(POSITION_ID, "Senior Backend Engineer", "Full-time", "Remote", "Remote",
                LocalDate.of(2021, 1, 1), null, true, "Owned backend services",
                List.of(new CvSourceResponsibility(RESPONSIBILITY_ID, "Led the backend team")),
                List.of(new CvSourceAchievement(ACHIEVEMENT_ID, "Owned the on-call rotation")),
                List.of());
        CvSourceCompany company = new CvSourceCompany(UUID.randomUUID(), "Acme Corp", "https://acme.test", "Fintech",
                "Remote", "A fintech company", List.of(position));
        CandidateProfileFacts profile = new CandidateProfileFacts(
                "Senior Backend Engineer", "Senior",
                List.of(new CandidateSkillFacts(SKILL_ID, "Java", "Language", null, SkillProficiency.EXPERT)),
                List.of(), 6,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null));
        return new CvSourceSnapshot(profile, CareerHistoryAvailability.AVAILABLE, List.of(company), List.of());
    }
}

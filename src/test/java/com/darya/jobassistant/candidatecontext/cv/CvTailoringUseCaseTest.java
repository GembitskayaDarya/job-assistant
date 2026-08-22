package com.darya.jobassistant.candidatecontext.cv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidatecontext.cv.baseline.BaselineCvSelectionProperties;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvDocument;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceAchievement;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceCompany;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePosition;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceResponsibility;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceSnapshot;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvSkillTailoringResult;
import com.darya.jobassistant.candidatecontext.cv.tailoring.ai.CvTailoringAiException;
import com.darya.jobassistant.candidatecontext.cv.tailoring.ai.CvTailoringAiPort;
import com.darya.jobassistant.candidatecontext.cv.tailoring.validation.CvTailoringValidationException;
import com.darya.jobassistant.candidatecontext.cv.tailoring.validation.CvTailoringViolation;
import com.darya.jobassistant.candidatecontext.cv.tailoring.validation.CvTailoringViolationCategory;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Sprint 11 Final CV Policy: end-to-end coverage of the whole content pipeline - {@code
 * CvSourceSnapshot -> BaselineCvSelectionResolver (fixed, non-skill content) + fake CvTailoringAiPort
 * (skill selection only) -> CvSkillCanonicalizationPolicy -> CvTailoringValidator -> CvAssembler ->
 * TailoredCvDocument} - using a fake {@link CvTailoringAiPort} rather than Spring AI. CV tailoring =
 * Technical Skills only, as of this block: everything this class used to be able to make the fake AI
 * port return directly (Professional Summary, position/project selection/rewrite) is now sourced
 * exclusively from {@link BaselineCvSelectionProperties} fixtures instead.
 */
class CvTailoringUseCaseTest {

    private static final UUID POSITION_ID = UUID.randomUUID();
    private static final UUID RESPONSIBILITY_ID = UUID.randomUUID();
    private static final UUID ACHIEVEMENT_ID = UUID.randomUUID();
    private static final UUID SKILL_ID = UUID.randomUUID();
    private static final UUID OTHER_SKILL_ID = UUID.randomUUID();

    /** Marker string standing in for anything private/free-text - candidate name, bullet text, vacancy description, etc. */
    private static final String POISON_TEXT = "POISON-MARKER-must-never-appear-in-a-log-line";

    private ListAppender<ILoggingEvent> logAppender;
    private Logger cvTailoringUseCaseLogger;

    @BeforeEach
    void captureLogs() {
        cvTailoringUseCaseLogger = (Logger) LoggerFactory.getLogger(CvTailoringUseCase.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        cvTailoringUseCaseLogger.addAppender(logAppender);
    }

    @AfterEach
    void releaseLogs() {
        cvTailoringUseCaseLogger.detachAppender(logAppender);
    }

    // ==================== Full pipeline ====================

    @Test
    void tailor_fullPipeline_validAiSkillOutput_baselineContentSurvivesUnchanged_aiSkillReplacesOnlySkills() {
        CvTailoringUseCase useCase = new CvTailoringUseCase(fakeSkillPort(List.of(SKILL_ID)), baselineProperties());

        TailoredCvDocument document = useCase.tailor(vacancy(), snapshot());

        assertThat(document.professionalSummary()).isEqualTo("Fixed approved summary");
        assertThat(document.skills()).containsExactly("Java");
        assertThat(document.experience().get(0).positions().get(0).responsibilities()).containsExactly("Led the backend team");
        assertThat(document.experience().get(0).positions().get(0).achievements()).containsExactly("Owned the on-call rotation");
    }

    /**
     * Part 18's assembly invariant: for the same factual source, the tailored document differs from
     * another tailored document produced with a different AI skill selection in Technical Skills
     * ONLY - every other field (header, summary, experience, Personal Projects, education,
     * languages) is identical, because both runs resolve the exact same baseline non-skill content.
     */
    @Test
    void tailor_twoDifferentAiSkillSelections_produceDocumentsThatDifferOnlyInSkills() {
        CvTailoringUseCase useCase = new CvTailoringUseCase(fakeSkillPort(List.of(SKILL_ID)), baselineProperties());
        CvTailoringUseCase otherUseCase = new CvTailoringUseCase(fakeSkillPort(List.of()), baselineProperties());

        TailoredCvDocument withSkill = useCase.tailor(vacancy(), snapshot());
        TailoredCvDocument withoutSkill = otherUseCase.tailor(vacancy(), snapshot());

        assertThat(withSkill.skills()).containsExactly("Java");
        assertThat(withoutSkill.skills()).isEmpty();
        assertThat(withSkill.header()).isEqualTo(withoutSkill.header());
        assertThat(withSkill.professionalSummary()).isEqualTo(withoutSkill.professionalSummary());
        assertThat(withSkill.experience()).isEqualTo(withoutSkill.experience());
        assertThat(withSkill.personalProjects()).isEqualTo(withoutSkill.personalProjects());
        assertThat(withSkill.education()).isEqualTo(withoutSkill.education());
        assertThat(withSkill.languages()).isEqualTo(withoutSkill.languages());
    }

    /**
     * Production incident regression test (Sprint 11 Final CV Policy fix): an empty/misconfigured
     * baseline (e.g. {@code baseline-cv-selection.yml} not mounted into the live container) must
     * fail the generation loudly and immediately - never silently produce a real, renderable
     * document missing every approved section. The AI port is never even called: the baseline is
     * resolved and validated before any skill-tailoring network call happens.
     */
    @Test
    void tailor_emptyBaselineConfiguration_failsLoudly_neverProducesASkeletonDocument() {
        CvTailoringUseCase useCase = new CvTailoringUseCase(fakeSkillPort(List.of(SKILL_ID)), emptyBaselineProperties());

        assertThatThrownBy(() -> useCase.tailor(vacancy(), snapshot()))
                .isInstanceOf(com.darya.jobassistant.candidatecontext.cv.baseline.BaselineCvSelectionResolutionException.class)
                .hasMessageContaining("no content at all");
    }

    @Test
    void tailor_aiPortThrowsCvTailoringAiException_propagatesUnchanged_neverReachesAssembly() {
        CvTailoringAiException providerFailure = new CvTailoringAiException("boom", new RuntimeException("network error"));
        CvTailoringUseCase useCase = new CvTailoringUseCase((vacancy, snapshot) -> {
            throw providerFailure;
        }, baselineProperties());

        assertThatThrownBy(() -> useCase.tailor(vacancy(), snapshot())).isSameAs(providerFailure);
    }

    @Test
    void tailor_unknownSkillIdFromAi_throwsCvTailoringValidationException_neverReachesAssembly() {
        UUID unknownSkill = UUID.randomUUID();
        CvTailoringUseCase useCase = new CvTailoringUseCase(fakeSkillPort(List.of(unknownSkill)), baselineProperties());

        assertThatThrownBy(() -> useCase.tailor(vacancy(), snapshot()))
                .isInstanceOf(CvTailoringValidationException.class)
                .satisfies(e -> assertThat(((CvTailoringValidationException) e).violations())
                        .extracting(CvTailoringViolation::category)
                        .containsExactly(CvTailoringViolationCategory.UNKNOWN_SKILL_REFERENCE));
    }

    // ==================== Production-diagnostics fix: violation logging ====================

    @Test
    void tailor_unknownSkillIdFromAi_logsOneStructuredWarnPerViolation_withCategoryAndIdOnly() {
        UUID unknownSkill = UUID.randomUUID();
        CvTailoringUseCase useCase = new CvTailoringUseCase(fakeSkillPort(List.of(unknownSkill)), baselineProperties());

        assertThatThrownBy(() -> useCase.tailor(vacancy(), snapshot())).isInstanceOf(CvTailoringValidationException.class);

        List<ILoggingEvent> violationLogs = capturedMessagesContaining("CV tailoring violation");
        assertThat(violationLogs).hasSize(1);
        assertThat(violationLogs).allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
        assertThat(violationLogs.get(0).getFormattedMessage())
                .contains("category=" + CvTailoringViolationCategory.UNKNOWN_SKILL_REFERENCE)
                .contains("referencedId=" + unknownSkill);
    }

    @Test
    void tailor_unknownSkillIdFromAi_loggedMessagesNeverContainCandidateOrVacancyFreeText() {
        UUID unknownSkill = UUID.randomUUID();
        CvTailoringUseCase useCase = new CvTailoringUseCase(fakeSkillPort(List.of(unknownSkill)), baselineProperties());

        assertThatThrownBy(() -> useCase.tailor(vacancyWithPoisonText(), snapshotWithPoisonText()))
                .isInstanceOf(CvTailoringValidationException.class);

        assertThat(logAppender.list)
                .as("no captured log line - at any level - may contain candidate/vacancy free text")
                .noneSatisfy(event -> assertThat(event.getFormattedMessage()).contains(POISON_TEXT));
    }

    @Test
    void logViolations_everyViolationCategory_canBeConstructedFromIdsAloneAndLoggedSafely() {
        CvTailoringUseCase useCase = new CvTailoringUseCase(fakeSkillPort(List.of()), baselineProperties());
        List<CvTailoringViolation> oneOfEachCategory = new ArrayList<>();
        for (CvTailoringViolationCategory category : CvTailoringViolationCategory.values()) {
            boolean isUnknownParentCategory = category.name().startsWith("UNKNOWN_");
            oneOfEachCategory.add(new CvTailoringViolation(category, UUID.randomUUID(), isUnknownParentCategory ? null : UUID.randomUUID()));
        }

        useCase.logViolations(vacancy(), oneOfEachCategory);

        List<ILoggingEvent> violationLogs = capturedMessagesContaining("CV tailoring violation");
        assertThat(violationLogs).hasSize(CvTailoringViolationCategory.values().length);
        for (CvTailoringViolationCategory category : CvTailoringViolationCategory.values()) {
            assertThat(violationLogs.stream().map(ILoggingEvent::getFormattedMessage))
                    .anyMatch(message -> message.contains("category=" + category));
        }
    }

    private List<ILoggingEvent> capturedMessagesContaining(String substring) {
        return logAppender.list.stream().filter(event -> event.getFormattedMessage().contains(substring)).toList();
    }

    // ==================== Bounded retry for stochastic malformed AI output (production hardening) ====================

    @Test
    void tailor_firstAttemptMalformed_secondSucceeds_returnsDocument() {
        CountingFakePort port = new CountingFakePort(List.of(malformed("attempt 1 malformed"), new CvSkillTailoringResult(List.of())));
        CvTailoringUseCase useCase = new CvTailoringUseCase(port, baselineProperties());

        TailoredCvDocument document = useCase.tailor(vacancy(), snapshot());

        assertThat(document).isNotNull();
        assertThat(port.callCount()).isEqualTo(2);
        assertThat(capturedMessagesContaining("CV skill tailoring attempt 1/3 started")).hasSize(1);
        assertThat(capturedMessagesContaining("CV skill tailoring attempt 1/3 rejected as malformed")).hasSize(1);
        assertThat(capturedMessagesContaining("CV skill tailoring attempt 2/3 started")).hasSize(1);
        assertThat(capturedMessagesContaining("CV skill tailoring succeeded on attempt 2/3")).hasSize(1);
    }

    @Test
    void tailor_firstTwoAttemptsMalformed_thirdSucceeds_returnsDocument() {
        CountingFakePort port = new CountingFakePort(List.of(
                malformed("attempt 1 malformed"), malformed("attempt 2 malformed"), new CvSkillTailoringResult(List.of())));
        CvTailoringUseCase useCase = new CvTailoringUseCase(port, baselineProperties());

        TailoredCvDocument document = useCase.tailor(vacancy(), snapshot());

        assertThat(document).isNotNull();
        assertThat(port.callCount()).isEqualTo(3);
        assertThat(capturedMessagesContaining("CV skill tailoring succeeded on attempt 3/3")).hasSize(1);
    }

    @Test
    void tailor_allThreeAttemptsMalformed_throwsFinalMalformedFailure_neverReachesAssembly() {
        CvTailoringAiException thirdFailure = malformed("attempt 3 malformed");
        CountingFakePort port = new CountingFakePort(List.of(malformed("attempt 1 malformed"), malformed("attempt 2 malformed"), thirdFailure));
        CvTailoringUseCase useCase = new CvTailoringUseCase(port, baselineProperties());

        assertThatThrownBy(() -> useCase.tailor(vacancy(), snapshot()))
                .isSameAs(thirdFailure)
                .extracting(e -> ((CvTailoringAiException) e).reason())
                .isEqualTo(CvTailoringAiException.Reason.MALFORMED_RESPONSE);

        assertThat(port.callCount()).isEqualTo(3);
        assertThat(capturedMessagesContaining("CV skill tailoring attempt 1/3 rejected as malformed")).hasSize(1);
        assertThat(capturedMessagesContaining("CV skill tailoring attempt 2/3 rejected as malformed")).hasSize(1);
        // The third (final) attempt's failure is logged as an error, never a silent "rejected as
        // malformed" retry line - there is no attempt 4 to retry into.
        assertThat(capturedMessagesContaining("CV skill tailoring attempt 3/3 rejected as malformed")).isEmpty();
    }

    @Test
    void tailor_providerFailure_isNeverRetried_exactlyOneAiCall() {
        CvTailoringAiException providerFailure = new CvTailoringAiException("boom", new RuntimeException("network error"));
        CountingFakePort port = new CountingFakePort(List.of(providerFailure));
        CvTailoringUseCase useCase = new CvTailoringUseCase(port, baselineProperties());

        assertThatThrownBy(() -> useCase.tailor(vacancy(), snapshot())).isSameAs(providerFailure);

        assertThat(port.callCount()).isEqualTo(1);
    }

    @Test
    void tailor_sourceValidationFailure_isNeverRetried_exactlyOneAiCall() {
        UUID unknownSkill = UUID.randomUUID();
        CountingFakePort port = new CountingFakePort(List.of(new CvSkillTailoringResult(List.of(unknownSkill))));
        CvTailoringUseCase useCase = new CvTailoringUseCase(port, baselineProperties());

        assertThatThrownBy(() -> useCase.tailor(vacancy(), snapshot())).isInstanceOf(CvTailoringValidationException.class);

        assertThat(port.callCount()).isEqualTo(1);
    }

    @Test
    void tailor_successfulFirstAttempt_exactlyOneAiCall() {
        CountingFakePort port = new CountingFakePort(List.of(new CvSkillTailoringResult(List.of())));
        CvTailoringUseCase useCase = new CvTailoringUseCase(port, baselineProperties());

        useCase.tailor(vacancy(), snapshot());

        assertThat(port.callCount()).isEqualTo(1);
        assertThat(capturedMessagesContaining("CV skill tailoring succeeded on attempt 1/3")).hasSize(1);
        assertThat(capturedMessagesContaining("rejected as malformed")).isEmpty();
    }

    @Test
    void tailor_unexpectedRuntimeException_propagatesUnmasked() {
        RuntimeException unexpected = new IllegalStateException("unexpected application error");
        CvTailoringUseCase useCase = new CvTailoringUseCase((vacancy, snapshot) -> {
            throw unexpected;
        }, baselineProperties());

        assertThatThrownBy(() -> useCase.tailor(vacancy(), snapshot())).isSameAs(unexpected);
    }

    private CvTailoringAiException malformed(String message) {
        return new CvTailoringAiException(message);
    }

    /** A {@link CvTailoringAiPort} fake that plays back one response (result or exception) per call, in order. */
    private static final class CountingFakePort implements CvTailoringAiPort {
        private final List<Object> responses;
        private int callCount = 0;

        CountingFakePort(List<Object> responses) {
            this.responses = responses;
        }

        @Override
        public CvSkillTailoringResult tailorSkills(JobOffer vacancy, CvSourceSnapshot snapshot) {
            Object response = responses.get(callCount);
            callCount++;
            if (response instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            return (CvSkillTailoringResult) response;
        }

        int callCount() {
            return callCount;
        }
    }

    // ==================== Fixtures ====================

    private CvTailoringAiPort fakeSkillPort(List<UUID> orderedSkillIds) {
        return (vacancy, snapshot) -> new CvSkillTailoringResult(orderedSkillIds);
    }

    /** Selects the fixture position's responsibilities/achievements in full, and a fixed summary - the non-skill baseline content. */
    private BaselineCvSelectionProperties baselineProperties() {
        return new BaselineCvSelectionProperties(
                "Fixed approved summary", List.of(),
                List.of(new BaselineCvSelectionProperties.PositionSelection(
                        "Acme Corp", "Senior Backend Engineer", List.of("*"), List.of("*"), List.of())),
                List.of(), null);
    }

    /**
     * No selections at all - now deliberately rejected by {@code BaselineCvSelectionResolver}'s
     * fail-loud guard (Sprint 11 Final CV Policy production fix). Used only by {@link
     * #tailor_emptyBaselineConfiguration_failsLoudly_neverProducesASkeletonDocument} - every other
     * test in this class uses {@link #baselineProperties()} instead.
     */
    private BaselineCvSelectionProperties emptyBaselineProperties() {
        return new BaselineCvSelectionProperties(null, List.of(), List.of(), List.of(), null);
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
                List.of(new CandidateSkillFacts(SKILL_ID, "Java", "Language", null, SkillProficiency.EXPERT),
                        new CandidateSkillFacts(OTHER_SKILL_ID, "Kafka", "Messaging", null, SkillProficiency.STRONG)),
                List.of(), 6,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null));
        return new CvSourceSnapshot(profile, CareerHistoryAvailability.AVAILABLE, List.of(company), List.of());
    }

    private JobOffer vacancyWithPoisonText() {
        return new JobOffer("job-1", "Backend Engineer", "Acme Corp", "Remote", null,
                POISON_TEXT, "https://example.com/job-1", "test");
    }

    /** Every free-text field (candidate name/contact, bullet/description text) is {@link #POISON_TEXT} - only ids/dates/booleans/enums are real. */
    private CvSourceSnapshot snapshotWithPoisonText() {
        CvSourcePosition position = new CvSourcePosition(POSITION_ID, "Senior Backend Engineer", "Full-time", "Remote", "Remote",
                LocalDate.of(2021, 1, 1), null, true, POISON_TEXT,
                List.of(new CvSourceResponsibility(RESPONSIBILITY_ID, POISON_TEXT)),
                List.of(new CvSourceAchievement(ACHIEVEMENT_ID, POISON_TEXT)),
                List.of());
        CvSourceCompany company = new CvSourceCompany(UUID.randomUUID(), "Acme Corp", "https://acme.test", "Fintech",
                "Remote", POISON_TEXT, List.of(position));
        CandidateProfileFacts profile = new CandidateProfileFacts(
                "Senior Backend Engineer", "Senior",
                List.of(new CandidateSkillFacts(SKILL_ID, "Java", "Language", null, SkillProficiency.EXPERT)),
                List.of(), 6,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null),
                POISON_TEXT, POISON_TEXT, POISON_TEXT, POISON_TEXT, POISON_TEXT, POISON_TEXT, List.of());
        return new CvSourceSnapshot(profile, CareerHistoryAvailability.AVAILABLE, List.of(company), List.of());
    }
}

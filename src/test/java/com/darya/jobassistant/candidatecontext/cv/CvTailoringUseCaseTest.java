package com.darya.jobassistant.candidatecontext.cv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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

    /** Marker string standing in for anything private/free-text - candidate name, bullet text, vacancy description, rewritten CV text, etc. */
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

    // ==================== Production-diagnostics fix: violation logging ====================

    @Test
    void tailor_invalidAiOutput_stillBlocksAssembly_diagnosticsChangeNeverWeakensThis() {
        UUID unknownSkill = UUID.randomUUID();
        CvTailoringResult invalidResult = new CvTailoringResult(null, List.of(unknownSkill), List.of(), List.of());
        CvTailoringUseCase useCase = new CvTailoringUseCase(fakePort(invalidResult));

        assertThatThrownBy(() -> useCase.tailor(vacancy(), snapshot())).isInstanceOf(CvTailoringValidationException.class);
    }

    @Test
    void tailor_invalidAiOutput_logsOneStructuredWarnPerViolation_withCategoryAndIdsOnly() {
        UUID unknownSkill = UUID.randomUUID();
        UUID unknownPosition = UUID.randomUUID();
        CvTailoringResult invalidResult = new CvTailoringResult(null, List.of(unknownSkill),
                List.of(new CvPositionTailoring(unknownPosition, List.of(), List.of())), List.of());
        CvTailoringUseCase useCase = new CvTailoringUseCase(fakePort(invalidResult));

        assertThatThrownBy(() -> useCase.tailor(vacancy(), snapshot())).isInstanceOf(CvTailoringValidationException.class);

        List<ILoggingEvent> violationLogs = capturedMessagesContaining("CV tailoring violation");
        assertThat(violationLogs).hasSize(2);
        assertThat(violationLogs).allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
        assertThat(violationLogs.stream().map(ILoggingEvent::getFormattedMessage))
                .anySatisfy(message -> assertThat(message)
                        .contains("category=" + CvTailoringViolationCategory.UNKNOWN_SKILL_REFERENCE)
                        .contains("referencedId=" + unknownSkill))
                .anySatisfy(message -> assertThat(message)
                        .contains("category=" + CvTailoringViolationCategory.UNKNOWN_POSITION_REFERENCE)
                        .contains("referencedId=" + unknownPosition));
    }

    @Test
    void tailor_invalidAiOutput_loggedMessagesNeverContainCandidateOrVacancyFreeText() {
        UUID responsibilityInWrongPosition = UUID.randomUUID();
        CvTailoringResult invalidResult = positionTailoringResult(POSITION_ID, List.of(responsibilityInWrongPosition));
        CvTailoringUseCase useCase = new CvTailoringUseCase(fakePort(invalidResult));

        assertThatThrownBy(() -> useCase.tailor(vacancyWithPoisonText(), snapshotWithPoisonText()))
                .isInstanceOf(CvTailoringValidationException.class);

        assertThat(logAppender.list)
                .as("no captured log line - at any level - may contain candidate/vacancy free text")
                .noneSatisfy(event -> assertThat(event.getFormattedMessage()).contains(POISON_TEXT));
    }

    @Test
    void logViolations_everyViolationCategory_canBeConstructedFromIdsAloneAndLoggedSafely() {
        CvTailoringUseCase useCase = new CvTailoringUseCase(fakePort(null));
        List<CvTailoringViolation> oneOfEachCategory = new ArrayList<>();
        for (CvTailoringViolationCategory category : CvTailoringViolationCategory.values()) {
            // No violation category requires any text field to exist - ids (and, for the three
            // UNKNOWN_*_REFERENCE categories, a null parentId) are always sufficient to represent it.
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
        CvTailoringResult validResult = new CvTailoringResult(null, List.of(), List.of(), List.of());
        CountingFakePort port = new CountingFakePort(List.of(malformed("attempt 1 malformed"), validResult));
        CvTailoringUseCase useCase = new CvTailoringUseCase(port);

        TailoredCvDocument document = useCase.tailor(vacancy(), snapshot());

        assertThat(document).isNotNull();
        assertThat(port.callCount()).isEqualTo(2);
        assertThat(capturedMessagesContaining("CV tailoring attempt 1/3 started")).hasSize(1);
        assertThat(capturedMessagesContaining("CV tailoring attempt 1/3 rejected as malformed")).hasSize(1);
        assertThat(capturedMessagesContaining("CV tailoring attempt 2/3 started")).hasSize(1);
        assertThat(capturedMessagesContaining("CV tailoring succeeded on attempt 2/3")).hasSize(1);
    }

    @Test
    void tailor_firstTwoAttemptsMalformed_thirdSucceeds_returnsDocument() {
        CvTailoringResult validResult = new CvTailoringResult(null, List.of(), List.of(), List.of());
        CountingFakePort port = new CountingFakePort(
                List.of(malformed("attempt 1 malformed"), malformed("attempt 2 malformed"), validResult));
        CvTailoringUseCase useCase = new CvTailoringUseCase(port);

        TailoredCvDocument document = useCase.tailor(vacancy(), snapshot());

        assertThat(document).isNotNull();
        assertThat(port.callCount()).isEqualTo(3);
        assertThat(capturedMessagesContaining("CV tailoring succeeded on attempt 3/3")).hasSize(1);
    }

    @Test
    void tailor_allThreeAttemptsMalformed_throwsFinalMalformedFailure_neverReachesAssembly() {
        CvTailoringAiException thirdFailure = malformed("attempt 3 malformed");
        CountingFakePort port = new CountingFakePort(List.of(malformed("attempt 1 malformed"), malformed("attempt 2 malformed"), thirdFailure));
        CvTailoringUseCase useCase = new CvTailoringUseCase(port);

        assertThatThrownBy(() -> useCase.tailor(vacancy(), snapshot()))
                .isSameAs(thirdFailure)
                .extracting(e -> ((CvTailoringAiException) e).reason())
                .isEqualTo(CvTailoringAiException.Reason.MALFORMED_RESPONSE);

        assertThat(port.callCount()).isEqualTo(3);
        assertThat(capturedMessagesContaining("CV tailoring attempt 1/3 rejected as malformed")).hasSize(1);
        assertThat(capturedMessagesContaining("CV tailoring attempt 2/3 rejected as malformed")).hasSize(1);
        // The third (final) attempt's failure is logged as an error, never a silent "rejected as
        // malformed" retry line - there is no attempt 4 to retry into.
        assertThat(capturedMessagesContaining("CV tailoring attempt 3/3 rejected as malformed")).isEmpty();
    }

    @Test
    void tailor_providerFailure_isNeverRetried_exactlyOneAiCall() {
        CvTailoringAiException providerFailure = new CvTailoringAiException("boom", new RuntimeException("network error"));
        CountingFakePort port = new CountingFakePort(List.of(providerFailure));
        CvTailoringUseCase useCase = new CvTailoringUseCase(port);

        assertThatThrownBy(() -> useCase.tailor(vacancy(), snapshot())).isSameAs(providerFailure);

        assertThat(port.callCount()).isEqualTo(1);
    }

    @Test
    void tailor_sourceValidationFailure_isNeverRetried_exactlyOneAiCall() {
        UUID unknownSkill = UUID.randomUUID();
        CvTailoringResult invalidResult = new CvTailoringResult(null, List.of(unknownSkill), List.of(), List.of());
        CountingFakePort port = new CountingFakePort(List.of(invalidResult));
        CvTailoringUseCase useCase = new CvTailoringUseCase(port);

        assertThatThrownBy(() -> useCase.tailor(vacancy(), snapshot())).isInstanceOf(CvTailoringValidationException.class);

        assertThat(port.callCount()).isEqualTo(1);
    }

    @Test
    void tailor_successfulFirstAttempt_exactlyOneAiCall() {
        CvTailoringResult validResult = new CvTailoringResult(null, List.of(), List.of(), List.of());
        CountingFakePort port = new CountingFakePort(List.of(validResult));
        CvTailoringUseCase useCase = new CvTailoringUseCase(port);

        useCase.tailor(vacancy(), snapshot());

        assertThat(port.callCount()).isEqualTo(1);
        assertThat(capturedMessagesContaining("CV tailoring succeeded on attempt 1/3")).hasSize(1);
        assertThat(capturedMessagesContaining("rejected as malformed")).isEmpty();
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
        public CvTailoringResult tailor(JobOffer vacancy, CvSourceSnapshot snapshot) {
            Object response = responses.get(callCount);
            callCount++;
            if (response instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            return (CvTailoringResult) response;
        }

        int callCount() {
            return callCount;
        }
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

    private CvTailoringResult positionTailoringResult(UUID positionId, List<UUID> responsibilityIds) {
        return new CvTailoringResult(null, List.of(), List.of(new CvPositionTailoring(positionId,
                responsibilityIds.stream().map(id -> new CvResponsibilityTailoring(id, null)).toList(), List.of())), List.of());
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

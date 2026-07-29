package com.darya.jobassistant.vacancyimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.AbstractIntegrationTest;
import com.darya.jobassistant.ai.entity.JobAnalysisEntity;
import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.ai.port.JobAnalysisAiPort;
import com.darya.jobassistant.ai.repository.JobAnalysisRepository;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import com.darya.jobassistant.vacancyextraction.model.ExtractedVacancyData;
import com.darya.jobassistant.vacancyextraction.model.RemotePolicy;
import com.darya.jobassistant.vacancyextraction.model.VacancyExtractionRequest;
import com.darya.jobassistant.vacancyextraction.port.VacancyExtractionPort;
import com.darya.jobassistant.vacancyimport.dto.AnalyzeImportedVacancyResult;
import com.darya.jobassistant.vacancyimport.dto.ContinueVacancyImportResult;
import com.darya.jobassistant.vacancyimport.dto.ReviewVacancyImportResult;
import com.darya.jobassistant.vacancyimport.dto.StartVacancyImportResult;
import com.darya.jobassistant.vacancyimport.model.ImportState;
import com.darya.jobassistant.vacancyimport.model.VacancyImportAction;
import com.darya.jobassistant.vacancyimport.repository.VacancyImportSessionRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The one integrated PostgreSQL workflow test for Sprint 6: proves the real, Spring-wired
 * application beans (not manually composed, as in {@link VacancyImportWorkflowEndToEndTest})
 * cooperate correctly against a real database across the full guided-import workflow, plus the
 * two cross-cutting PostgreSQL guarantees that only show up when two independent import sessions
 * interact through shared tables - URL-based Vacancy deduplication and one JobAnalysis per
 * Vacancy - which no single-session repository test can exercise. Everything already proven at
 * the repository level (individual conditional transitions, constraints) is deliberately not
 * repeated here.
 *
 * <p>{@link VacancyExtractionPort} and {@link JobAnalysisAiPort} are replaced with {@link
 * MockitoBean} mocks so this test never calls OpenAI, matching every other automated test in this
 * project.
 */
class VacancyImportWorkflowIntegrationTest extends AbstractIntegrationTest {

    private static final long CHAT_ID = 5001L;
    private static final long USER_ID = 6001L;
    private static final long OTHER_CHAT_ID = 5002L;
    private static final long OTHER_USER_ID = 6002L;
    private static final String URL = "https://example.com/jobs/integration-test-vacancy";
    private static final String DESCRIPTION =
            "We are looking for a Senior Java Backend Engineer to join our platform team. Kafka and AWS experience required.";

    @Autowired
    private StartVacancyImportUseCase startVacancyImportUseCase;

    @Autowired
    private ContinueVacancyImportUseCase continueVacancyImportUseCase;

    @Autowired
    private ReviewVacancyImportUseCase reviewVacancyImportUseCase;

    @Autowired
    private AnalyzeImportedVacancyUseCase analyzeImportedVacancyUseCase;

    @Autowired
    private VacancyImportSessionRepository sessionRepository;

    @Autowired
    private VacancyRepository vacancyRepository;

    @Autowired
    private JobAnalysisRepository jobAnalysisRepository;

    @MockitoBean
    private VacancyExtractionPort vacancyExtractionPort;

    @MockitoBean
    private JobAnalysisAiPort jobAnalysisAiPort;

    @Test
    void guidedImportWorkflow_savedAndAnalyzedThroughRealSpringWiredBeansAgainstRealPostgres() {
        ExtractedVacancyData extracted = new ExtractedVacancyData(
                "Senior Java Backend Engineer", "Integration Test Co", "Remote", RemotePolicy.REMOTE,
                List.of("B2B"), List.of("Java", "Kafka"), null);
        when(vacancyExtractionPort.extract(any(VacancyExtractionRequest.class))).thenReturn(extracted);
        JobAnalysis analysis = new JobAnalysis(
                90, List.of("Strong match"), List.of(), List.of(), List.of(),
                "6 years vs. no stated requirement.", "Remote preference matches.", "Great fit");
        when(jobAnalysisAiPort.analyze(any(), any())).thenReturn(analysis);

        UUID sessionId = startImport(CHAT_ID, USER_ID);
        provideUrlAndDescription(sessionId, CHAT_ID, USER_ID);

        ReviewVacancyImportResult saveResult = reviewVacancyImportUseCase.review(sessionId, CHAT_ID, USER_ID, VacancyImportAction.SAVE);
        assertThat(saveResult).isInstanceOf(ReviewVacancyImportResult.Saved.class);
        boolean firstNewlyCreated = ((ReviewVacancyImportResult.Saved) saveResult).newlyCreated();
        assertThat(firstNewlyCreated).isTrue();

        var completedSession = sessionRepository.findSessionById(sessionId).orElseThrow();
        assertThat(completedSession.getState()).isEqualTo(ImportState.COMPLETED);
        UUID vacancyId = completedSession.getVacancyId();
        assertThat(vacancyId).isNotNull();
        Vacancy persistedVacancy = vacancyRepository.findByIdWithCompany(vacancyId).orElseThrow();
        assertThat(persistedVacancy.getDescription()).isEqualTo(DESCRIPTION);
        assertThat(persistedVacancy.getUrl()).isEqualTo(URL);

        // --- URL-based deduplication: a second, independent import session for the same URL
        // must reuse the same Vacancy row rather than creating a second one. ---
        UUID secondSessionId = startImport(OTHER_CHAT_ID, OTHER_USER_ID);
        provideUrlAndDescription(secondSessionId, OTHER_CHAT_ID, OTHER_USER_ID);
        ReviewVacancyImportResult secondSaveResult =
                reviewVacancyImportUseCase.review(secondSessionId, OTHER_CHAT_ID, OTHER_USER_ID, VacancyImportAction.SAVE);
        assertThat(secondSaveResult).isInstanceOf(ReviewVacancyImportResult.Saved.class);
        assertThat(((ReviewVacancyImportResult.Saved) secondSaveResult).newlyCreated()).isFalse();
        UUID secondSessionVacancyId = sessionRepository.findSessionById(secondSessionId).orElseThrow().getVacancyId();
        assertThat(secondSessionVacancyId).isEqualTo(vacancyId);
        assertThat(vacancyRepository.findAll().stream().filter(v -> v.getUrl().equals(URL)).count()).isEqualTo(1);

        // --- Analyze, then repeated Analyze: exactly one JobAnalysis row, one AI call. ---
        AnalyzeImportedVacancyResult analyzeResult = analyzeImportedVacancyUseCase.analyze(sessionId, CHAT_ID, USER_ID);
        assertThat(analyzeResult).isInstanceOf(AnalyzeImportedVacancyResult.Available.class);
        assertThat(((AnalyzeImportedVacancyResult.Available) analyzeResult).newlyCreated()).isTrue();

        JobAnalysisEntity persistedAnalysis = jobAnalysisRepository.findByVacancyId(vacancyId).orElseThrow();
        assertThat(persistedAnalysis.getScore()).isEqualTo(90);

        AnalyzeImportedVacancyResult repeated = analyzeImportedVacancyUseCase.analyze(sessionId, CHAT_ID, USER_ID);
        assertThat(repeated).isInstanceOf(AnalyzeImportedVacancyResult.Available.class);
        assertThat(((AnalyzeImportedVacancyResult.Available) repeated).newlyCreated()).isFalse();
        verify(jobAnalysisAiPort, times(1)).analyze(any(), any());

        // Analyzing through the second (deduplicated) session's linked vacancy must return the
        // very same persisted analysis, never a second claim/row for the same vacancy.
        AnalyzeImportedVacancyResult analyzeViaOtherSession = analyzeImportedVacancyUseCase.analyze(secondSessionId, OTHER_CHAT_ID, OTHER_USER_ID);
        assertThat(analyzeViaOtherSession).isInstanceOf(AnalyzeImportedVacancyResult.Available.class);
        assertThat(((AnalyzeImportedVacancyResult.Available) analyzeViaOtherSession).newlyCreated()).isFalse();
        verify(jobAnalysisAiPort, times(1)).analyze(any(), any());
    }

    private UUID startImport(long chatId, long userId) {
        StartVacancyImportResult startResult = startVacancyImportUseCase.start(chatId, userId);
        assertThat(startResult).isInstanceOf(StartVacancyImportResult.Started.class);
        return ((StartVacancyImportResult.Started) startResult).sessionId();
    }

    private void provideUrlAndDescription(UUID sessionId, long chatId, long userId) {
        ContinueVacancyImportResult urlResult = continueVacancyImportUseCase.handleText(chatId, userId, URL);
        assertThat(urlResult).isInstanceOf(ContinueVacancyImportResult.UrlAccepted.class);

        ContinueVacancyImportResult descriptionResult = continueVacancyImportUseCase.handleText(chatId, userId, DESCRIPTION);
        assertThat(descriptionResult).isInstanceOf(ContinueVacancyImportResult.VacancyRecognized.class);

        var session = sessionRepository.findSessionById(sessionId).orElseThrow();
        assertThat(session.getState()).isEqualTo(ImportState.WAITING_FOR_CONFIRMATION);
    }
}

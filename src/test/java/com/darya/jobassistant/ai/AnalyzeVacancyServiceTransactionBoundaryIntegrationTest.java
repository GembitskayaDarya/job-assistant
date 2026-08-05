package com.darya.jobassistant.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.AbstractIntegrationTest;
import com.darya.jobassistant.ai.dto.AnalyzeVacancyResult;
import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.ai.port.JobAnalysisAiPort;
import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sprint 9 Step 8 final correction: proves - against real, Spring-wired beans and a real
 * PostgreSQL-backed {@code CandidateContextProvider} (not a fake/mock standing in for the
 * transaction boundary) - that the AI call {@link AnalyzeVacancyUseCase#analyze} makes is never
 * issued from inside an open database transaction.
 *
 * <p>Unlike a test that only asserts {@code isActualTransactionActive()} immediately after {@code
 * CandidateContextProvider#loadCurrentContext()} returns (which only proves that one method's own
 * boundary closes, not that nothing later re-opens one before the AI call), this test captures the
 * transaction-active flag from <em>inside</em> the {@link JobAnalysisAiPort#analyze} invocation
 * itself - the exact seam requested: if the AI call were ever moved inside the read transaction (a
 * regression), this assertion would fail even though every individual class's own annotations
 * still looked correct in isolation.
 */
class AnalyzeVacancyServiceTransactionBoundaryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AnalyzeVacancyUseCase analyzeVacancyUseCase;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private VacancyRepository vacancyRepository;

    @MockitoBean
    private JobAnalysisAiPort jobAnalysisAiPort;

    @Test
    void analyze_noTransactionIsActiveInsideTheAiPortInvocation() {
        AtomicBoolean transactionActiveDuringAiCall = new AtomicBoolean(true);
        AtomicInteger aiCallCount = new AtomicInteger(0);
        JobAnalysis analysis = new JobAnalysis(
                80, List.of("Strong match"), List.of(), List.of(), List.of(),
                "6 years vs. no stated requirement.", "Remote preference matches.", "Good fit");
        when(jobAnalysisAiPort.analyze(anyString(), anyString())).thenAnswer(invocation -> {
            aiCallCount.incrementAndGet();
            transactionActiveDuringAiCall.set(TransactionSynchronizationManager.isActualTransactionActive());
            return analysis;
        });

        Vacancy vacancy = persistVacancy();

        AnalyzeVacancyResult result = analyzeVacancyUseCase.analyze(vacancy.getId());

        assertThat(result).isInstanceOf(AnalyzeVacancyResult.Available.class);
        assertThat(aiCallCount.get())
                .as("exactly one AI invocation for one analysis")
                .isEqualTo(1);
        verify(jobAnalysisAiPort, times(1)).analyze(any(), any());
        assertThat(transactionActiveDuringAiCall.get())
                .as("no database transaction must be active while JobAnalysisAiPort.analyze runs - "
                        + "the read transaction that loaded the Candidate Context must already be closed by then")
                .isFalse();
    }

    private Vacancy persistVacancy() {
        Company company = companyRepository.save(Company.builder().name("Acme " + UUID.randomUUID()).build());
        String url = "https://example.com/job/" + UUID.randomUUID();
        return vacancyRepository.save(Vacancy.builder()
                .company(company)
                .title("Backend Engineer")
                .description("Build backend services")
                .url(url)
                .canonicalUrl(url)
                .source("remoteok")
                .build());
    }
}

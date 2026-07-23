package com.darya.jobassistant.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.integrations.jobsource.JobSourceException;
import com.darya.jobassistant.integrations.jobsource.JobSourcePort;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.service.VacancyIngestionService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VacancyIngestionJobTest {

    @Mock
    private JobSourcePort jobSourcePort;

    @Mock
    private VacancyIngestionService vacancyIngestionService;

    private VacancyIngestionJob vacancyIngestionJob;

    @BeforeEach
    void setUp() {
        vacancyIngestionJob = new VacancyIngestionJob(List.of(jobSourcePort), vacancyIngestionService);
    }

    @Test
    void ingest_delegatesEachFetchedJobOfferToTheIngestionService() {
        JobOffer offerOne = jobOffer("https://example.com/job-1");
        JobOffer offerTwo = jobOffer("https://example.com/job-2");
        when(jobSourcePort.sourceName()).thenReturn("remoteok");
        when(jobSourcePort.fetchLatestPostings()).thenReturn(List.of(offerOne, offerTwo));
        when(vacancyIngestionService.persist(any(JobOffer.class)))
                .thenReturn(Vacancy.builder().id(UUID.randomUUID()).build());

        vacancyIngestionJob.ingest();

        verify(vacancyIngestionService).persist(offerOne);
        verify(vacancyIngestionService).persist(offerTwo);
        verifyNoMoreInteractions(vacancyIngestionService);
    }

    @Test
    void ingest_sourceFetchFailure_isIsolatedAndDoesNotPropagate() {
        when(jobSourcePort.sourceName()).thenReturn("remoteok");
        when(jobSourcePort.fetchLatestPostings()).thenThrow(new JobSourceException("boom"));

        vacancyIngestionJob.ingest();

        verify(vacancyIngestionService, never()).persist(any());
    }

    @Test
    void ingest_onePersistenceFailure_doesNotPreventOtherOffersFromBeingPersisted() {
        JobOffer failing = jobOffer("https://example.com/job-1");
        JobOffer succeeding = jobOffer("https://example.com/job-2");
        when(jobSourcePort.sourceName()).thenReturn("remoteok");
        when(jobSourcePort.fetchLatestPostings()).thenReturn(List.of(failing, succeeding));
        when(vacancyIngestionService.persist(failing)).thenThrow(new IllegalArgumentException("malformed"));
        when(vacancyIngestionService.persist(succeeding)).thenReturn(Vacancy.builder().id(UUID.randomUUID()).build());

        vacancyIngestionJob.ingest();

        verify(vacancyIngestionService, times(1)).persist(eq(succeeding));
    }

    private JobOffer jobOffer(String url) {
        return new JobOffer("job-1", "Backend Engineer", "Acme", "Remote", "120k", "desc", url, "remoteok");
    }
}

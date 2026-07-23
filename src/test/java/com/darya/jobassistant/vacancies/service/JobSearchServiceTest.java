package com.darya.jobassistant.vacancies.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.integrations.jobsource.JobSourceException;
import com.darya.jobassistant.integrations.jobsource.JobSourcePort;
import com.darya.jobassistant.vacancies.dto.SearchResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobSearchServiceTest {

    @Mock
    private JobSourcePort jobSourcePort;

    @Mock
    private VacancyIngestionService vacancyIngestionService;

    private JobSearchService jobSearchService;

    @BeforeEach
    void setUp() {
        jobSearchService = new JobSearchService(List.of(jobSourcePort), vacancyIngestionService);
    }

    @Test
    void search_filtersByKeywordAcrossTitleCompanyAndDescription() {
        JobOffer matching = jobOffer("Backend Engineer", "Acme", "Build things", "https://example.com/1");
        JobOffer nonMatching = jobOffer("Frontend Engineer", "Other Co", "React work", "https://example.com/2");
        when(jobSourcePort.fetchLatestPostings()).thenReturn(List.of(matching, nonMatching));
        UUID vacancyId = UUID.randomUUID();
        when(vacancyIngestionService.persist(matching)).thenReturn(Vacancy.builder().id(vacancyId).build());

        List<SearchResult> results = jobSearchService.search("backend");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).jobOffer()).isSameAs(matching);
        assertThat(results.get(0).vacancyId()).isEqualTo(vacancyId);
        verify(vacancyIngestionService, never()).persist(nonMatching);
    }

    @Test
    void search_persistsEachMatchingJobOfferAndReturnsItsVacancyUuid() {
        JobOffer job = jobOffer("Backend Engineer", "Acme", "Build things", "https://example.com/1");
        when(jobSourcePort.fetchLatestPostings()).thenReturn(List.of(job));
        UUID vacancyId = UUID.randomUUID();
        when(vacancyIngestionService.persist(job)).thenReturn(Vacancy.builder().id(vacancyId).build());

        List<SearchResult> results = jobSearchService.search("backend");

        verify(vacancyIngestionService).persist(job);
        assertThat(results).singleElement().satisfies(r -> assertThat(r.vacancyId()).isEqualTo(vacancyId));
    }

    @Test
    void search_sourceFetchFailure_isIsolatedAndReturnsEmptyForThatSource() {
        when(jobSourcePort.sourceName()).thenReturn("remoteok");
        when(jobSourcePort.fetchLatestPostings()).thenThrow(new JobSourceException("boom"));

        List<SearchResult> results = jobSearchService.search("backend");

        assertThat(results).isEmpty();
        verify(vacancyIngestionService, never()).persist(any());
    }

    @Test
    void search_persistenceFailureForOneOffer_doesNotDiscardOtherValidResults() {
        JobOffer failing = jobOffer("Backend Engineer", "Acme", "desc", "https://example.com/1");
        JobOffer succeeding = jobOffer("Backend Developer", "Beta", "desc", "https://example.com/2");
        when(jobSourcePort.fetchLatestPostings()).thenReturn(List.of(failing, succeeding));
        UUID vacancyId = UUID.randomUUID();
        when(vacancyIngestionService.persist(failing)).thenThrow(new IllegalArgumentException("malformed"));
        when(vacancyIngestionService.persist(succeeding)).thenReturn(Vacancy.builder().id(vacancyId).build());

        List<SearchResult> results = jobSearchService.search("backend");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).jobOffer()).isSameAs(succeeding);
    }

    @Test
    void search_noMatches_returnsEmptyList() {
        JobOffer nonMatching = jobOffer("Frontend Engineer", "Other Co", "React work", "https://example.com/2");
        when(jobSourcePort.fetchLatestPostings()).thenReturn(List.of(nonMatching));

        List<SearchResult> results = jobSearchService.search("backend");

        assertThat(results).isEmpty();
        verify(vacancyIngestionService, never()).persist(any());
    }

    private JobOffer jobOffer(String title, String company, String description, String url) {
        return new JobOffer("job-1", title, company, "Remote", "120k", description, url, "remoteok");
    }
}

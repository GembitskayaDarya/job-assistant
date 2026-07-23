package com.darya.jobassistant.telegram.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.vacancies.dto.SearchResult;
import com.darya.jobassistant.vacancies.service.JobSearchService;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@ExtendWith(MockitoExtension.class)
class SearchCommandTest {

    @Mock
    private JobSearchService jobSearchService;

    @Mock
    private Message message;

    private SearchCommand searchCommand;

    @BeforeEach
    void setUp() {
        searchCommand = new SearchCommand(jobSearchService);
    }

    @Test
    void execute_delegatesKeywordToJobSearchServiceAndFormatsResult() {
        when(message.getText()).thenReturn("/search backend");
        UUID vacancyId = UUID.randomUUID();
        SearchResult result = new SearchResult(jobOffer("Backend Engineer", "Acme Corp", "$100k-$120k"), vacancyId);
        when(jobSearchService.search("backend")).thenReturn(List.of(result));

        BotResponse response = searchCommand.execute(message);

        verify(jobSearchService).search("backend");
        assertThat(response.text()).contains("Backend Engineer");
        assertThat(response.text()).contains("Acme Corp");
        assertThat(response.text()).contains("$100k-$120k");
        assertThat(response.text()).contains("https://example.com/job-1");
        assertThat(response.text()).contains(vacancyId.toString());
        assertThat(response.text()).contains("Analyze: /analyze " + vacancyId);
    }

    @Test
    void execute_blankKeyword_doesNotCallJobSearchService() {
        when(message.getText()).thenReturn("/search");

        BotResponse response = searchCommand.execute(message);

        assertThat(response.text()).contains("Please provide a keyword");
        verifyNoInteractions(jobSearchService);
    }

    @Test
    void execute_noResults_returnsNoJobsFoundMessage() {
        when(message.getText()).thenReturn("/search nonexistent");
        when(jobSearchService.search("nonexistent")).thenReturn(List.of());

        BotResponse response = searchCommand.execute(message);

        assertThat(response.text()).contains("No jobs found");
    }

    @Test
    void execute_limitsOutputToFiveResults() {
        when(message.getText()).thenReturn("/search backend");
        List<SearchResult> sixResults = IntStream.range(0, 6)
                .mapToObj(i -> new SearchResult(
                        jobOffer("Backend Engineer " + i, "Acme Corp", "$100k"), UUID.randomUUID()))
                .toList();
        when(jobSearchService.search("backend")).thenReturn(sixResults);

        BotResponse response = searchCommand.execute(message);

        for (int i = 0; i < 5; i++) {
            assertThat(response.text()).contains("Backend Engineer " + i);
        }
        assertThat(response.text()).doesNotContain("Backend Engineer 5");
    }

    private JobOffer jobOffer(String title, String company, String salary) {
        return new JobOffer("job-1", title, company, "Remote", salary, "desc", "https://example.com/job-1", "remoteok");
    }
}

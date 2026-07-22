package com.darya.jobassistant.integrations.jobsource.remoteok;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.integrations.jobsource.JobSourceException;
import java.io.IOException;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class RemoteOkJobSourceAdapterTest {

    private MockWebServer server;
    private RemoteOkJobSourceAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        RemoteOkProperties properties = new RemoteOkProperties(server.url("/api").toString());
        adapter = new RemoteOkJobSourceAdapter(WebClient.create(), properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void sourceName_returnsRemoteok() {
        assertThat(adapter.sourceName()).isEqualTo("remoteok");
    }

    @Test
    void fetchLatestPostings_mapsJsonIntoJobOffersAndDropsLegalNotice() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        [
                          {"legal": "https://remoteok.com/legal"},
                          {
                            "id": "123",
                            "company": "Acme",
                            "position": "Backend Engineer",
                            "location": "Worldwide",
                            "description": "Build things",
                            "url": "https://remoteok.com/remote-jobs/123",
                            "salary_min": 50000,
                            "salary_max": 90000
                          }
                        ]
                        """));

        List<JobOffer> offers = adapter.fetchLatestPostings();

        assertThat(offers).hasSize(1);
        JobOffer offer = offers.get(0);
        assertThat(offer.id()).isEqualTo("123");
        assertThat(offer.title()).isEqualTo("Backend Engineer");
        assertThat(offer.company()).isEqualTo("Acme");
        assertThat(offer.location()).isEqualTo("Worldwide");
        assertThat(offer.salary()).isEqualTo("$50,000 - $90,000");
        assertThat(offer.description()).isEqualTo("Build things");
        assertThat(offer.url()).isEqualTo("https://remoteok.com/remote-jobs/123");
        assertThat(offer.source()).isEqualTo("remoteok");
    }

    @Test
    void fetchLatestPostings_dropsEntriesMissingRequiredFields() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        [
                          {"id": "1", "company": "", "position": "Engineer", "url": "https://x"},
                          {"id": "2", "company": "Acme", "position": "Engineer", "url": ""}
                        ]
                        """));

        List<JobOffer> offers = adapter.fetchLatestPostings();

        assertThat(offers).isEmpty();
    }

    @Test
    void fetchLatestPostings_formatsSingleSalaryBoundWhenOnlyOneIsPresent() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        [{"id": "1", "company": "Acme", "position": "Engineer", "url": "https://x", "salary_min": 90000}]
                        """));

        List<JobOffer> offers = adapter.fetchLatestPostings();

        assertThat(offers.get(0).salary()).isEqualTo("$90,000");
    }

    @Test
    void fetchLatestPostings_leavesSalaryNullWhenNeitherBoundPresent() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        [{"id": "1", "company": "Acme", "position": "Engineer", "url": "https://x"}]
                        """));

        List<JobOffer> offers = adapter.fetchLatestPostings();

        assertThat(offers.get(0).salary()).isNull();
    }

    @Test
    void fetchLatestPostings_throwsJobSourceExceptionOnClientError() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("rate limited"));

        assertThatThrownBy(() -> adapter.fetchLatestPostings())
                .isInstanceOf(JobSourceException.class)
                .hasMessageContaining("429")
                .hasMessageContaining("rate limited");
    }

    @Test
    void fetchLatestPostings_throwsJobSourceExceptionOnServerError() {
        server.enqueue(new MockResponse().setResponseCode(503));

        assertThatThrownBy(() -> adapter.fetchLatestPostings())
                .isInstanceOf(JobSourceException.class)
                .hasMessageContaining("503");
    }

    @Test
    void fetchLatestPostings_throwsJobSourceExceptionOnMalformedJson() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("not json"));

        assertThatThrownBy(() -> adapter.fetchLatestPostings())
                .isInstanceOf(JobSourceException.class);
    }
}

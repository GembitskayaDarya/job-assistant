package com.darya.jobassistant.integrations.jobsource;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(JobSourceController.class)
class JobSourceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobSourcePort jobSourcePort;

    @Test
    void listSources_returnsConfiguredSourceNames() throws Exception {
        when(jobSourcePort.sourceName()).thenReturn("remoteok");

        mockMvc.perform(get("/api/job-sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("remoteok"));
    }

    @Test
    void fetchJobs_returnsOffersFromMatchingSource() throws Exception {
        when(jobSourcePort.sourceName()).thenReturn("remoteok");
        JobOffer offer = new JobOffer("1", "Engineer", "Acme", "Remote", "$90,000", "desc", "https://x", "remoteok");
        when(jobSourcePort.fetchLatestPostings()).thenReturn(List.of(offer));

        mockMvc.perform(get("/api/job-sources/remoteok/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].company").value("Acme"));
    }

    @Test
    void fetchJobs_isCaseInsensitiveOnSourceName() throws Exception {
        when(jobSourcePort.sourceName()).thenReturn("remoteok");
        when(jobSourcePort.fetchLatestPostings()).thenReturn(List.of());

        mockMvc.perform(get("/api/job-sources/RemoteOK/jobs"))
                .andExpect(status().isOk());
    }

    @Test
    void fetchJobs_returnsNotFoundForUnknownSource() throws Exception {
        when(jobSourcePort.sourceName()).thenReturn("remoteok");

        mockMvc.perform(get("/api/job-sources/greenhouse/jobs"))
                .andExpect(status().isNotFound());
    }

    @Test
    void fetchJobs_returnsBadGatewayWhenSourceFails() throws Exception {
        when(jobSourcePort.sourceName()).thenReturn("remoteok");
        when(jobSourcePort.fetchLatestPostings()).thenThrow(new JobSourceException("RemoteOK is down"));

        mockMvc.perform(get("/api/job-sources/remoteok/jobs"))
                .andExpect(status().isBadGateway());
    }
}

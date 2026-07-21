package com.darya.jobassistant.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.darya.jobassistant.dto.JobApplicationRequest;
import com.darya.jobassistant.dto.JobApplicationResponse;
import com.darya.jobassistant.entity.ApplicationStatus;
import com.darya.jobassistant.service.JobApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(JobApplicationController.class)
class JobApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JobApplicationService jobApplicationService;

    @Test
    void create_returnsCreatedResponse() throws Exception {
        JobApplicationRequest request = new JobApplicationRequest(
                "Acme", "Backend Engineer", ApplicationStatus.APPLIED, LocalDate.now(), 111L, null);
        JobApplicationResponse response = new JobApplicationResponse(
                1L, "Acme", "Backend Engineer", ApplicationStatus.APPLIED, request.appliedDate(), 111L, null, null, null);
        when(jobApplicationService.create(request)).thenReturn(response);

        mockMvc.perform(post("/api/job-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.company").value("Acme"));
    }

    @Test
    void getAll_returnsList() throws Exception {
        JobApplicationResponse response = new JobApplicationResponse(
                1L, "Acme", "Backend Engineer", ApplicationStatus.APPLIED, LocalDate.now(), 111L, null, null, null);
        when(jobApplicationService.getAll()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/job-applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void create_rejectsInvalidRequest() throws Exception {
        String invalidJson = "{\"company\":\"\",\"position\":\"\"}";

        mockMvc.perform(post("/api/job-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }
}

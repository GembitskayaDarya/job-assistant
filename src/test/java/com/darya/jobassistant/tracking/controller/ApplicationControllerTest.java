package com.darya.jobassistant.tracking.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.darya.jobassistant.tracking.dto.ApplicationRequest;
import com.darya.jobassistant.tracking.dto.ApplicationResponse;
import com.darya.jobassistant.tracking.entity.ApplicationStatus;
import com.darya.jobassistant.tracking.service.ApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ApplicationController.class)
class ApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ApplicationService applicationService;

    @Test
    void create_returnsCreatedResponse() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        ApplicationRequest request = new ApplicationRequest(
                companyId, null, 111L, ApplicationStatus.APPLIED, LocalDate.now(), null);
        ApplicationResponse response = new ApplicationResponse(
                applicationId, companyId, "Acme", null, null, 111L, ApplicationStatus.APPLIED, request.appliedDate(), null, null, null);
        when(applicationService.create(request)).thenReturn(response);

        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyName").value("Acme"));
    }

    @Test
    void getAll_returnsList() throws Exception {
        ApplicationResponse response = new ApplicationResponse(
                UUID.randomUUID(), UUID.randomUUID(), "Acme", null, null, 111L,
                ApplicationStatus.APPLIED, LocalDate.now(), null, null, null);
        when(applicationService.getAll()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void create_rejectsInvalidRequest() throws Exception {
        String invalidJson = "{\"companyId\":null,\"status\":null}";

        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }
}

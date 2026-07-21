package com.darya.jobassistant.tracking.controller;

import com.darya.jobassistant.tracking.dto.ApplicationRequest;
import com.darya.jobassistant.tracking.dto.ApplicationResponse;
import com.darya.jobassistant.tracking.service.ApplicationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody ApplicationRequest request) {
        ApplicationResponse response = applicationService.create(request);
        return ResponseEntity.created(URI.create("/api/applications/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public ApplicationResponse getById(@PathVariable UUID id) {
        return applicationService.getById(id);
    }

    @GetMapping
    public List<ApplicationResponse> getAll() {
        return applicationService.getAll();
    }

    @PutMapping("/{id}")
    public ApplicationResponse update(@PathVariable UUID id, @Valid @RequestBody ApplicationRequest request) {
        return applicationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        applicationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

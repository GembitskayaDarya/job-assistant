package com.darya.jobassistant.tracking.service;

import com.darya.jobassistant.tracking.dto.ApplicationRequest;
import com.darya.jobassistant.tracking.dto.ApplicationResponse;
import java.util.List;
import java.util.UUID;

public interface ApplicationService {

    ApplicationResponse create(ApplicationRequest request);

    ApplicationResponse getById(UUID id);

    List<ApplicationResponse> getAll();

    List<ApplicationResponse> findByTelegramChatId(Long telegramChatId);

    ApplicationResponse update(UUID id, ApplicationRequest request);

    void delete(UUID id);
}

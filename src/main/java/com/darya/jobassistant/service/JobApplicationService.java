package com.darya.jobassistant.service;

import com.darya.jobassistant.dto.JobApplicationRequest;
import com.darya.jobassistant.dto.JobApplicationResponse;
import java.util.List;

public interface JobApplicationService {

    JobApplicationResponse create(JobApplicationRequest request);

    JobApplicationResponse getById(Long id);

    List<JobApplicationResponse> getAll();

    List<JobApplicationResponse> findByTelegramChatId(Long telegramChatId);

    JobApplicationResponse update(Long id, JobApplicationRequest request);

    void delete(Long id);
}

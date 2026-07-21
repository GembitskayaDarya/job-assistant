package com.darya.jobassistant.service;

import com.darya.jobassistant.dto.JobApplicationRequest;
import com.darya.jobassistant.dto.JobApplicationResponse;
import com.darya.jobassistant.entity.JobApplication;
import com.darya.jobassistant.repository.JobApplicationRepository;
import com.darya.jobassistant.util.JobApplicationMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class JobApplicationServiceImpl implements JobApplicationService {

    private final JobApplicationRepository jobApplicationRepository;

    @Override
    public JobApplicationResponse create(JobApplicationRequest request) {
        JobApplication entity = JobApplicationMapper.toEntity(request);
        JobApplication saved = jobApplicationRepository.save(entity);
        return JobApplicationMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public JobApplicationResponse getById(Long id) {
        return jobApplicationRepository.findById(id)
                .map(JobApplicationMapper::toResponse)
                .orElseThrow(() -> new JobApplicationNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobApplicationResponse> getAll() {
        return jobApplicationRepository.findAll().stream()
                .map(JobApplicationMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobApplicationResponse> findByTelegramChatId(Long telegramChatId) {
        return jobApplicationRepository.findByTelegramChatId(telegramChatId).stream()
                .map(JobApplicationMapper::toResponse)
                .toList();
    }

    @Override
    public JobApplicationResponse update(Long id, JobApplicationRequest request) {
        JobApplication entity = jobApplicationRepository.findById(id)
                .orElseThrow(() -> new JobApplicationNotFoundException(id));
        JobApplicationMapper.updateEntity(entity, request);
        return JobApplicationMapper.toResponse(entity);
    }

    @Override
    public void delete(Long id) {
        if (!jobApplicationRepository.existsById(id)) {
            throw new JobApplicationNotFoundException(id);
        }
        jobApplicationRepository.deleteById(id);
    }
}

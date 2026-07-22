package com.darya.jobassistant.tracking.service;

import com.darya.jobassistant.exception.ApplicationNotFoundException;
import com.darya.jobassistant.telegram.user.User;
import com.darya.jobassistant.telegram.user.UserRepository;
import com.darya.jobassistant.tracking.dto.ApplicationRequest;
import com.darya.jobassistant.tracking.dto.ApplicationResponse;
import com.darya.jobassistant.tracking.entity.Application;
import com.darya.jobassistant.tracking.mapper.ApplicationMapper;
import com.darya.jobassistant.tracking.repository.ApplicationRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final ApplicationMapper applicationMapper;

    public ApplicationResponse create(ApplicationRequest request) {
        Application entity = applicationMapper.toEntity(request);
        entity.setUser(resolveUser(request.telegramChatId()));
        Application saved = applicationRepository.save(entity);
        return applicationMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getById(UUID id) {
        return applicationRepository.findById(id)
                .map(applicationMapper::toResponse)
                .orElseThrow(() -> new ApplicationNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> getAll() {
        return applicationRepository.findAll().stream()
                .map(applicationMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> findByTelegramChatId(Long telegramChatId) {
        return applicationRepository.findByUserTelegramId(telegramChatId).stream()
                .map(applicationMapper::toResponse)
                .toList();
    }

    public ApplicationResponse update(UUID id, ApplicationRequest request) {
        Application entity = applicationRepository.findById(id)
                .orElseThrow(() -> new ApplicationNotFoundException(id));
        applicationMapper.updateEntity(entity, request);
        entity.setUser(resolveUser(request.telegramChatId()));
        return applicationMapper.toResponse(entity);
    }

    public void delete(UUID id) {
        if (!applicationRepository.existsById(id)) {
            throw new ApplicationNotFoundException(id);
        }
        applicationRepository.deleteById(id);
    }

    private User resolveUser(Long telegramId) {
        if (telegramId == null) {
            return null;
        }
        return userRepository.findByTelegramId(telegramId)
                .orElseGet(() -> userRepository.save(User.builder().telegramId(telegramId).build()));
    }
}

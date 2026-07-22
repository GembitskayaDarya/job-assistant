package com.darya.jobassistant.tracking.service;

import com.darya.jobassistant.exception.ApplicationNotFoundException;
import com.darya.jobassistant.telegram.user.TelegramUser;
import com.darya.jobassistant.telegram.user.TelegramUserRepository;
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
    private final TelegramUserRepository telegramUserRepository;
    private final ApplicationMapper applicationMapper;

    public ApplicationResponse create(ApplicationRequest request) {
        Application entity = applicationMapper.toEntity(request);
        entity.setTelegramUser(resolveTelegramUser(request.telegramChatId()));
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
        return applicationRepository.findByTelegramUserChatId(telegramChatId).stream()
                .map(applicationMapper::toResponse)
                .toList();
    }

    public ApplicationResponse update(UUID id, ApplicationRequest request) {
        Application entity = applicationRepository.findById(id)
                .orElseThrow(() -> new ApplicationNotFoundException(id));
        applicationMapper.updateEntity(entity, request);
        entity.setTelegramUser(resolveTelegramUser(request.telegramChatId()));
        return applicationMapper.toResponse(entity);
    }

    public void delete(UUID id) {
        if (!applicationRepository.existsById(id)) {
            throw new ApplicationNotFoundException(id);
        }
        applicationRepository.deleteById(id);
    }

    private TelegramUser resolveTelegramUser(Long chatId) {
        if (chatId == null) {
            return null;
        }
        return telegramUserRepository.findByChatId(chatId)
                .orElseGet(() -> telegramUserRepository.save(TelegramUser.builder().chatId(chatId).build()));
    }
}

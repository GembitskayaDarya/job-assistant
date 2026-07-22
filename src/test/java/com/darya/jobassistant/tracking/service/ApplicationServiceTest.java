package com.darya.jobassistant.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.exception.ApplicationNotFoundException;
import com.darya.jobassistant.telegram.user.User;
import com.darya.jobassistant.telegram.user.UserRepository;
import com.darya.jobassistant.tracking.dto.ApplicationRequest;
import com.darya.jobassistant.tracking.dto.ApplicationResponse;
import com.darya.jobassistant.tracking.entity.Application;
import com.darya.jobassistant.tracking.entity.ApplicationStatus;
import com.darya.jobassistant.tracking.mapper.ApplicationMapper;
import com.darya.jobassistant.tracking.repository.ApplicationRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationMapper applicationMapper;

    private ApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new ApplicationService(applicationRepository, userRepository, applicationMapper);
    }

    @Test
    void create_registersNewUserWhenTelegramChatIdProvided() {
        UUID companyId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        Company acme = Company.builder().id(companyId).name("Acme").build();
        ApplicationRequest request = new ApplicationRequest(
                companyId, null, 111L, ApplicationStatus.APPLIED, LocalDate.now(), null);
        Application entity = Application.builder().company(acme).status(ApplicationStatus.APPLIED).build();
        Application saved = Application.builder().id(applicationId).company(acme).status(ApplicationStatus.APPLIED).build();
        ApplicationResponse response = new ApplicationResponse(
                applicationId, companyId, "Acme", null, null, 111L, ApplicationStatus.APPLIED, request.appliedDate(), null, null, null);

        when(applicationMapper.toEntity(request)).thenReturn(entity);
        when(userRepository.findByTelegramId(111L)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenReturn(User.builder().id(UUID.randomUUID()).telegramId(111L).build());
        when(applicationRepository.save(entity)).thenReturn(saved);
        when(applicationMapper.toResponse(saved)).thenReturn(response);

        ApplicationResponse result = applicationService.create(request);

        assertThat(result.id()).isEqualTo(applicationId);
        assertThat(result.telegramChatId()).isEqualTo(111L);
    }

    @Test
    void getById_throwsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(applicationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.getById(id))
                .isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    void delete_removesExistingApplication() {
        UUID id = UUID.randomUUID();
        when(applicationRepository.existsById(id)).thenReturn(true);

        applicationService.delete(id);

        verify(applicationRepository).deleteById(id);
    }
}

package com.darya.jobassistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.dto.JobApplicationRequest;
import com.darya.jobassistant.dto.JobApplicationResponse;
import com.darya.jobassistant.entity.ApplicationStatus;
import com.darya.jobassistant.entity.JobApplication;
import com.darya.jobassistant.repository.JobApplicationRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobApplicationServiceImplTest {

    @Mock
    private JobApplicationRepository jobApplicationRepository;

    private JobApplicationServiceImpl jobApplicationService;

    @BeforeEach
    void setUp() {
        jobApplicationService = new JobApplicationServiceImpl(jobApplicationRepository);
    }

    @Test
    void create_savesAndReturnsResponse() {
        JobApplicationRequest request = new JobApplicationRequest(
                "Acme", "Backend Engineer", ApplicationStatus.APPLIED, LocalDate.now(), 111L, null);
        JobApplication saved = JobApplication.builder()
                .id(1L)
                .company("Acme")
                .position("Backend Engineer")
                .status(ApplicationStatus.APPLIED)
                .appliedDate(request.appliedDate())
                .telegramChatId(111L)
                .build();
        when(jobApplicationRepository.save(any(JobApplication.class))).thenReturn(saved);

        JobApplicationResponse response = jobApplicationService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.company()).isEqualTo("Acme");
    }

    @Test
    void getById_throwsWhenMissing() {
        when(jobApplicationRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobApplicationService.getById(42L))
                .isInstanceOf(JobApplicationNotFoundException.class);
    }

    @Test
    void delete_removesExistingApplication() {
        when(jobApplicationRepository.existsById(1L)).thenReturn(true);

        jobApplicationService.delete(1L);

        verify(jobApplicationRepository).deleteById(1L);
    }
}

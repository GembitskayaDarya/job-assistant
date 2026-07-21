package com.darya.jobassistant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.entity.ApplicationStatus;
import com.darya.jobassistant.entity.JobApplication;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class JobApplicationRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Test
    void findByTelegramChatId_returnsOnlyMatchingApplications() {
        jobApplicationRepository.save(JobApplication.builder()
                .company("Acme")
                .position("Backend Engineer")
                .status(ApplicationStatus.APPLIED)
                .appliedDate(LocalDate.now())
                .telegramChatId(111L)
                .build());
        jobApplicationRepository.save(JobApplication.builder()
                .company("Globex")
                .position("Platform Engineer")
                .status(ApplicationStatus.INTERVIEW)
                .appliedDate(LocalDate.now())
                .telegramChatId(222L)
                .build());

        List<JobApplication> results = jobApplicationRepository.findByTelegramChatId(111L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCompany()).isEqualTo("Acme");
    }
}

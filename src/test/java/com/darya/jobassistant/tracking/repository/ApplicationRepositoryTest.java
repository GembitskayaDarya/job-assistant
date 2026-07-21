package com.darya.jobassistant.tracking.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.telegram.entity.TelegramUser;
import com.darya.jobassistant.telegram.repository.TelegramUserRepository;
import com.darya.jobassistant.tracking.entity.Application;
import com.darya.jobassistant.tracking.entity.ApplicationStatus;
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
class ApplicationRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private TelegramUserRepository telegramUserRepository;

    @Test
    void findByTelegramUserChatId_returnsOnlyMatchingApplications() {
        Company acme = companyRepository.save(Company.builder().name("Acme").build());
        Company globex = companyRepository.save(Company.builder().name("Globex").build());
        TelegramUser user = telegramUserRepository.save(TelegramUser.builder().chatId(111L).build());

        applicationRepository.save(Application.builder()
                .company(acme)
                .telegramUser(user)
                .status(ApplicationStatus.APPLIED)
                .appliedDate(LocalDate.now())
                .build());
        applicationRepository.save(Application.builder()
                .company(globex)
                .status(ApplicationStatus.INTERVIEW)
                .appliedDate(LocalDate.now())
                .build());

        List<Application> results = applicationRepository.findByTelegramUserChatId(111L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCompany().getName()).isEqualTo("Acme");
    }
}

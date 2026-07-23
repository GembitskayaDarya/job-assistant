package com.darya.jobassistant.vacancies.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VacancyJobOfferMapperTest {

    private final VacancyJobOfferMapper mapper = new VacancyJobOfferMapper();

    @Test
    void toJobOffer_mapsAllAvailableFields() {
        Company company = Company.builder().name("Acme Corp").build();
        Vacancy vacancy = Vacancy.builder()
                .company(company)
                .title("Backend Engineer")
                .description("Great job")
                .url("https://example.com/job-1")
                .salaryMin(BigDecimal.valueOf(100_000))
                .salaryMax(BigDecimal.valueOf(120_000))
                .currency("USD")
                .source("remoteok")
                .build();
        vacancy.setId(UUID.randomUUID());

        JobOffer jobOffer = mapper.toJobOffer(vacancy);

        assertThat(jobOffer.id()).isEqualTo(vacancy.getId().toString());
        assertThat(jobOffer.title()).isEqualTo("Backend Engineer");
        assertThat(jobOffer.company()).isEqualTo("Acme Corp");
        assertThat(jobOffer.location()).isNull();
        assertThat(jobOffer.salary()).isEqualTo("100000 - 120000 USD");
        assertThat(jobOffer.description()).isEqualTo("Great job");
        assertThat(jobOffer.url()).isEqualTo("https://example.com/job-1");
        assertThat(jobOffer.source()).isEqualTo("remoteok");
    }

    @Test
    void toJobOffer_noSalaryPersisted_returnsNullSalary() {
        Company company = Company.builder().name("Acme Corp").build();
        Vacancy vacancy = Vacancy.builder()
                .company(company)
                .title("Backend Engineer")
                .description("Great job")
                .url("https://example.com/job-1")
                .source("remoteok")
                .build();
        vacancy.setId(UUID.randomUUID());

        JobOffer jobOffer = mapper.toJobOffer(vacancy);

        assertThat(jobOffer.salary()).isNull();
    }
}

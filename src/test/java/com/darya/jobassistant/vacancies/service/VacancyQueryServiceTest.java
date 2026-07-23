package com.darya.jobassistant.vacancies.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.exception.VacancyNotFoundException;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VacancyQueryServiceTest {

    @Mock
    private VacancyRepository vacancyRepository;

    private VacancyQueryService vacancyQueryService;

    @BeforeEach
    void setUp() {
        vacancyQueryService = new VacancyQueryService(vacancyRepository);
    }

    @Test
    void getById_found_returnsVacancy() {
        UUID id = UUID.randomUUID();
        Vacancy vacancy = Vacancy.builder().build();
        when(vacancyRepository.findByIdWithCompany(id)).thenReturn(Optional.of(vacancy));

        Vacancy result = vacancyQueryService.getById(id);

        assertThat(result).isSameAs(vacancy);
    }

    @Test
    void getById_notFound_throwsVacancyNotFoundException() {
        UUID id = UUID.randomUUID();
        when(vacancyRepository.findByIdWithCompany(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vacancyQueryService.getById(id))
                .isInstanceOf(VacancyNotFoundException.class);
    }
}

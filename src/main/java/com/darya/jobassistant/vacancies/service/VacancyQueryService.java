package com.darya.jobassistant.vacancies.service;

import com.darya.jobassistant.exception.VacancyNotFoundException;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VacancyQueryService {

    private final VacancyRepository vacancyRepository;

    public Vacancy getById(UUID id) {
        return vacancyRepository.findByIdWithCompany(id)
                .orElseThrow(() -> new VacancyNotFoundException(id));
    }
}

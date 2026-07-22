package com.darya.jobassistant.vacancies.service;

import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.mapper.VacancyMapper;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class VacancyService {

    private final VacancyRepository vacancyRepository;
    private final VacancyMapper vacancyMapper;

    public boolean existsByUrl(String url) {
        return vacancyRepository.existsByUrl(url);
    }

    public void save(Vacancy vacancy) {
        vacancyRepository.save(vacancy);
    }
}

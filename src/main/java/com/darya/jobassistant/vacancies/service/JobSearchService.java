package com.darya.jobassistant.vacancies.service;

import com.darya.jobassistant.vacancies.dto.VacancyResponse;
import com.darya.jobassistant.vacancies.mapper.VacancyMapper;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobSearchService {

    private final VacancyRepository vacancyRepository;
    private final VacancyMapper vacancyMapper;

    public List<VacancyResponse> search(String keyword) {
        return vacancyRepository.findByTitleContainingIgnoreCase(keyword).stream()
                .map(vacancyMapper::toResponse)
                .toList();
    }
}

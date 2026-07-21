package com.darya.jobassistant.vacancies.repository;

import com.darya.jobassistant.vacancies.entity.Vacancy;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VacancyRepository extends JpaRepository<Vacancy, UUID> {
}

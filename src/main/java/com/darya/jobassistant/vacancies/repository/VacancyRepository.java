package com.darya.jobassistant.vacancies.repository;

import com.darya.jobassistant.vacancies.entity.Vacancy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VacancyRepository extends JpaRepository<Vacancy, UUID> {

    List<Vacancy> findByCompanyId(UUID companyId);

    List<Vacancy> findByTitleContainingIgnoreCase(String title);

    Optional<Vacancy> findByUrl(String url);

    @Query("select v from Vacancy v join fetch v.company where v.id = :id")
    Optional<Vacancy> findByIdWithCompany(UUID id);
}

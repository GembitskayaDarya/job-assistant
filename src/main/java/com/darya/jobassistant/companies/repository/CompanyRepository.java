package com.darya.jobassistant.companies.repository;

import com.darya.jobassistant.companies.entity.Company;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    Optional<Company> findByNameIgnoreCase(String name);

    List<Company> findByNameContainingIgnoreCase(String name);
}

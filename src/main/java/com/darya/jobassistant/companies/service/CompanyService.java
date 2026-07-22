package com.darya.jobassistant.companies.service;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.mapper.CompanyMapper;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final CompanyMapper companyMapper;

    public Company findOrCreateByName(String name) {
        return companyRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> companyRepository.save(Company.builder().name(name).build()));
    }
}

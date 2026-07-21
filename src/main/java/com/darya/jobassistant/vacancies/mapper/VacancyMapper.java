package com.darya.jobassistant.vacancies.mapper;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.exception.CompanyNotFoundException;
import com.darya.jobassistant.mapper.EntityMapper;
import com.darya.jobassistant.vacancies.dto.VacancyRequest;
import com.darya.jobassistant.vacancies.dto.VacancyResponse;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VacancyMapper implements EntityMapper<Vacancy, VacancyRequest, VacancyResponse> {

    private final CompanyRepository companyRepository;

    @Override
    public Vacancy toEntity(VacancyRequest request) {
        return Vacancy.builder()
                .company(resolveCompany(request.companyId()))
                .title(request.title())
                .description(request.description())
                .url(request.url())
                .salaryMin(request.salaryMin())
                .salaryMax(request.salaryMax())
                .currency(request.currency())
                .source(request.source())
                .postedAt(request.postedAt())
                .build();
    }

    @Override
    public void updateEntity(Vacancy entity, VacancyRequest request) {
        entity.setCompany(resolveCompany(request.companyId()));
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setUrl(request.url());
        entity.setSalaryMin(request.salaryMin());
        entity.setSalaryMax(request.salaryMax());
        entity.setCurrency(request.currency());
        entity.setSource(request.source());
        entity.setPostedAt(request.postedAt());
    }

    @Override
    public VacancyResponse toResponse(Vacancy entity) {
        return new VacancyResponse(
                entity.getId(),
                entity.getCompany().getId(),
                entity.getCompany().getName(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getUrl(),
                entity.getSalaryMin(),
                entity.getSalaryMax(),
                entity.getCurrency(),
                entity.getSource(),
                entity.getPostedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private Company resolveCompany(UUID companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyNotFoundException(companyId));
    }
}

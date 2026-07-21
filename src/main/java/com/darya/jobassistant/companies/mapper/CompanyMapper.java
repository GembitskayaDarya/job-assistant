package com.darya.jobassistant.companies.mapper;

import com.darya.jobassistant.companies.dto.CompanyRequest;
import com.darya.jobassistant.companies.dto.CompanyResponse;
import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.mapper.EntityMapper;
import org.springframework.stereotype.Component;

@Component
public class CompanyMapper implements EntityMapper<Company, CompanyRequest, CompanyResponse> {

    @Override
    public Company toEntity(CompanyRequest request) {
        return Company.builder()
                .name(request.name())
                .website(request.website())
                .notes(request.notes())
                .build();
    }

    @Override
    public void updateEntity(Company entity, CompanyRequest request) {
        entity.setName(request.name());
        entity.setWebsite(request.website());
        entity.setNotes(request.notes());
    }

    @Override
    public CompanyResponse toResponse(Company entity) {
        return new CompanyResponse(
                entity.getId(),
                entity.getName(),
                entity.getWebsite(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

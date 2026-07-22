package com.darya.jobassistant.tracking.mapper;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.exception.CompanyNotFoundException;
import com.darya.jobassistant.exception.VacancyNotFoundException;
import com.darya.jobassistant.mapper.EntityMapper;
import com.darya.jobassistant.tracking.dto.ApplicationRequest;
import com.darya.jobassistant.tracking.dto.ApplicationResponse;
import com.darya.jobassistant.tracking.entity.Application;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves {@code companyId}/{@code vacancyId} into managed entities. The user
 * relation is intentionally left untouched here — linking/creating a {@code User}
 * from a raw Telegram id is a find-or-create write operation owned by the service layer.
 */
@Component
@RequiredArgsConstructor
public class ApplicationMapper implements EntityMapper<Application, ApplicationRequest, ApplicationResponse> {

    private final CompanyRepository companyRepository;
    private final VacancyRepository vacancyRepository;

    @Override
    public Application toEntity(ApplicationRequest request) {
        return Application.builder()
                .company(resolveCompany(request.companyId()))
                .vacancy(resolveVacancy(request.vacancyId()))
                .status(request.status())
                .appliedDate(request.appliedDate())
                .notes(request.notes())
                .build();
    }

    @Override
    public void updateEntity(Application entity, ApplicationRequest request) {
        entity.setCompany(resolveCompany(request.companyId()));
        entity.setVacancy(resolveVacancy(request.vacancyId()));
        entity.setStatus(request.status());
        entity.setAppliedDate(request.appliedDate());
        entity.setNotes(request.notes());
    }

    @Override
    public ApplicationResponse toResponse(Application entity) {
        Vacancy vacancy = entity.getVacancy();
        return new ApplicationResponse(
                entity.getId(),
                entity.getCompany().getId(),
                entity.getCompany().getName(),
                vacancy != null ? vacancy.getId() : null,
                vacancy != null ? vacancy.getTitle() : null,
                entity.getUser() != null ? entity.getUser().getTelegramId() : null,
                entity.getStatus(),
                entity.getAppliedDate(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private Company resolveCompany(UUID companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyNotFoundException(companyId));
    }

    private Vacancy resolveVacancy(UUID vacancyId) {
        if (vacancyId == null) {
            return null;
        }
        return vacancyRepository.findById(vacancyId)
                .orElseThrow(() -> new VacancyNotFoundException(vacancyId));
    }
}

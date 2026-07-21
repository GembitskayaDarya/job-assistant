package com.darya.jobassistant.interviews.mapper;

import com.darya.jobassistant.exception.ApplicationNotFoundException;
import com.darya.jobassistant.interviews.dto.InterviewRequest;
import com.darya.jobassistant.interviews.dto.InterviewResponse;
import com.darya.jobassistant.interviews.entity.Interview;
import com.darya.jobassistant.mapper.EntityMapper;
import com.darya.jobassistant.tracking.entity.Application;
import com.darya.jobassistant.tracking.repository.ApplicationRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InterviewMapper implements EntityMapper<Interview, InterviewRequest, InterviewResponse> {

    private final ApplicationRepository applicationRepository;

    @Override
    public Interview toEntity(InterviewRequest request) {
        return Interview.builder()
                .application(resolveApplication(request.applicationId()))
                .scheduledAt(request.scheduledAt())
                .type(request.type())
                .status(request.status())
                .notes(request.notes())
                .build();
    }

    @Override
    public void updateEntity(Interview entity, InterviewRequest request) {
        entity.setApplication(resolveApplication(request.applicationId()));
        entity.setScheduledAt(request.scheduledAt());
        entity.setType(request.type());
        entity.setStatus(request.status());
        entity.setNotes(request.notes());
    }

    @Override
    public InterviewResponse toResponse(Interview entity) {
        return new InterviewResponse(
                entity.getId(),
                entity.getApplication().getId(),
                entity.getScheduledAt(),
                entity.getType(),
                entity.getStatus(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private Application resolveApplication(UUID applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException(applicationId));
    }
}

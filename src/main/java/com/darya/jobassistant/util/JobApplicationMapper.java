package com.darya.jobassistant.util;

import com.darya.jobassistant.dto.JobApplicationRequest;
import com.darya.jobassistant.dto.JobApplicationResponse;
import com.darya.jobassistant.entity.JobApplication;

public final class JobApplicationMapper {

    private JobApplicationMapper() {
    }

    public static JobApplication toEntity(JobApplicationRequest request) {
        return JobApplication.builder()
                .company(request.company())
                .position(request.position())
                .status(request.status())
                .appliedDate(request.appliedDate())
                .telegramChatId(request.telegramChatId())
                .notes(request.notes())
                .build();
    }

    public static void updateEntity(JobApplication entity, JobApplicationRequest request) {
        entity.setCompany(request.company());
        entity.setPosition(request.position());
        entity.setStatus(request.status());
        entity.setAppliedDate(request.appliedDate());
        entity.setTelegramChatId(request.telegramChatId());
        entity.setNotes(request.notes());
    }

    public static JobApplicationResponse toResponse(JobApplication entity) {
        return new JobApplicationResponse(
                entity.getId(),
                entity.getCompany(),
                entity.getPosition(),
                entity.getStatus(),
                entity.getAppliedDate(),
                entity.getTelegramChatId(),
                entity.getNotes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
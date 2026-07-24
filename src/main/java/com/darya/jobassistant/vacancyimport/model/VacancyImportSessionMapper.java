package com.darya.jobassistant.vacancyimport.model;

import com.darya.jobassistant.vacancyimport.entity.VacancyImportSessionEntity;

/**
 * Maps between {@link VacancyImportSession} and {@link VacancyImportSessionEntity}. Lives in
 * this package (not {@code repository}) specifically so it can call {@link
 * VacancyImportSession#reconstruct}, which is package-private on purpose: reconstruction from a
 * trusted, already-validated row must not be reachable from outside this boundary.
 */
public final class VacancyImportSessionMapper {

    private VacancyImportSessionMapper() {}

    public static VacancyImportSessionEntity toEntity(VacancyImportSession session) {
        return VacancyImportSessionEntity.builder()
                .id(session.getId())
                .isNew(session.isNewSession())
                .telegramChatId(session.getTelegramChatId())
                .telegramUserId(session.getTelegramUserId())
                .state(session.getState())
                .sourceUrl(session.getSourceUrl())
                .rawDescription(session.getRawDescription())
                .vacancyId(session.getVacancyId())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .expiresAt(session.getExpiresAt())
                .build();
    }

    public static VacancyImportSession toDomain(VacancyImportSessionEntity entity) {
        return VacancyImportSession.reconstruct(
                entity.getId(),
                entity.getTelegramChatId(),
                entity.getTelegramUserId(),
                entity.getState(),
                entity.getSourceUrl(),
                entity.getRawDescription(),
                entity.getVacancyId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getExpiresAt());
    }
}

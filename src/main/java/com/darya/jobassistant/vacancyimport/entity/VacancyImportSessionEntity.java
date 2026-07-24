package com.darya.jobassistant.vacancyimport.entity;

import com.darya.jobassistant.entity.BaseEntity;
import com.darya.jobassistant.vacancyimport.model.ImportState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "vacancy_import_session")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class VacancyImportSessionEntity extends BaseEntity {

    @Column(name = "telegram_chat_id", nullable = false)
    private Long telegramChatId;

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ImportState state;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "raw_description", columnDefinition = "TEXT")
    private String rawDescription;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}

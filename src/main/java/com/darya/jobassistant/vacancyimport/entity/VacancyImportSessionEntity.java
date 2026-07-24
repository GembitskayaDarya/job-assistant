package com.darya.jobassistant.vacancyimport.entity;

import com.darya.jobassistant.entity.BaseEntity;
import com.darya.jobassistant.vacancyimport.model.ImportState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.domain.Persistable;

/**
 * Implements {@link Persistable} because this entity's id is client-generated (see {@code
 * VacancyImportSession}), so Spring Data's default "id == null means new" heuristic can never
 * fire here. {@code isNew} is populated once, directly by {@code VacancyImportSessionMapper} from
 * the domain session's own new-vs-rehydrated distinction - not by {@code @PostLoad}/{@code
 * @PostPersist} callbacks, since the mapper builds a fresh entity instance on every call rather
 * than mutating one long-lived JPA-managed object across load and save.
 */
@Entity
@Table(name = "vacancy_import_session")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class VacancyImportSessionEntity extends BaseEntity implements Persistable<UUID> {

    @Transient
    private boolean isNew;

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

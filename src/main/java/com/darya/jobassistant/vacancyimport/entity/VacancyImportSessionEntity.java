package com.darya.jobassistant.vacancyimport.entity;

import com.darya.jobassistant.vacancyimport.model.ImportState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Does not extend the shared {@code BaseEntity} - deliberately. {@code BaseEntity}'s {@code id}
 * is {@code @GeneratedValue(strategy = GenerationType.UUID)}, which every other entity in this
 * project relies on to let Hibernate generate the id. This entity's id is always client-assigned
 * (see {@code VacancyImportSession#start}, which must produce a session with an id before it is
 * ever persisted), and JPA attribute overrides can't strip a {@code @GeneratedValue} inherited
 * from a mapped superclass - so reusing {@code BaseEntity} here would leave the id "generated" in
 * Hibernate's eyes even though we always populate it ourselves. That combination makes Hibernate's
 * {@code persist()} treat a non-null, self-assigned id as a detached instance passed in by
 * mistake ({@code PersistentObjectException: detached entity passed to persist}). Declaring
 * {@code id} here with no {@code @GeneratedValue} makes it an "assigned" identifier instead,
 * which is exactly what a client-generated id is.
 *
 * <p>Implements {@link Persistable} for the same underlying reason: Spring Data's default
 * "id == null means new" heuristic can never fire for a client-generated id. {@code isNew} is
 * populated once, directly by {@code VacancyImportSessionMapper} from the domain session's own
 * new-vs-rehydrated distinction - not by {@code @PostLoad}/{@code @PostPersist} callbacks, since
 * the mapper builds a fresh entity instance on every call rather than mutating one long-lived
 * JPA-managed object across load and save.
 */
@Entity
@Table(name = "vacancy_import_session")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class VacancyImportSessionEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    @Column(name = "vacancy_id")
    private UUID vacancyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}

package com.darya.jobassistant.vacancyimport.entity;

import com.darya.jobassistant.vacancyextraction.model.RemotePolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Does not extend the shared {@code BaseEntity}: {@code BaseEntity}'s {@code createdAt}/{@code
 * updatedAt} are populated by {@code AuditingEntityListener} from the system clock, but the draft
 * and its owning session's {@code EXTRACTING -> WAITING_FOR_CONFIRMATION} transition must be
 * stamped with the same, test-injectable {@code Clock} the rest of {@code VacancyImportService}
 * uses - exactly like {@code VacancyImportSessionEntity}'s {@code updated_at}. Plain
 * {@code @Column} fields populated explicitly by {@code VacancyImportDraftRepository.saveDraft}
 * give full control over that value instead.
 *
 * <p>List fields use PostgreSQL {@code text[]} columns via {@code @JdbcTypeCode(SqlTypes.ARRAY)},
 * the same technique already established by {@code JobAnalysisEntity} for {@code pros}/{@code
 * cons}/{@code missingSkills}. These lists exist purely for a preview display and are never
 * queried or filtered on individually, so a normalized child table would add join complexity
 * with no read benefit, and JSONB would add a mapping layer this project doesn't otherwise use for
 * list-of-string data - the existing array convention is the deliberate, consistent choice.
 */
@Entity
@Table(name = "vacancy_import_draft")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacancyImportDraftEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 300)
    private String company;

    @Column(length = 300)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "remote_policy", nullable = false, length = 20)
    private RemotePolicy remotePolicy;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "contract_types", columnDefinition = "text[]")
    private List<String> contractTypes;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "required_skills", columnDefinition = "text[]")
    private List<String> requiredSkills;

    @Column(name = "salary_text", length = 200)
    private String salaryText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

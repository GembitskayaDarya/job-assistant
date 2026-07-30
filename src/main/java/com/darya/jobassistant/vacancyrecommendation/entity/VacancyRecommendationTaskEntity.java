package com.darya.jobassistant.vacancyrecommendation.entity;

import com.darya.jobassistant.entity.BaseEntity;
import com.darya.jobassistant.vacancyrecommendation.VacancyRecommendationFailureCategory;
import com.darya.jobassistant.vacancyrecommendation.VacancyRecommendationTaskOutcome;
import com.darya.jobassistant.vacancyrecommendation.VacancyRecommendationTaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * The durable work item behind Sprint 8 Step 10's automatic recommendation pipeline: one row per
 * web-discovery {@code Vacancy} that was newly {@code CREATED} (see {@code
 * VacancyIngestionService#persistDiscovered}), atomically committed alongside that {@code Vacancy}
 * in the same transaction. {@code id}/{@code createdAt}/{@code updatedAt} come from {@link
 * BaseEntity}; every other column is owned and explicitly maintained by {@code
 * VacancyRecommendationTaskRepository}'s native claim/transition queries (a JPQL/native bulk
 * update never touches {@code @LastModifiedDate} automatically, so {@code updatedAt} is always set
 * explicitly in those queries too).
 *
 * <p>{@code status}/{@code outcome}/{@code lastErrorCategory} are mapped {@code
 * EnumType.STRING} - the database stores and validates (via V15's CHECK constraints) the exact
 * stable enum names, never an ordinal.
 */
@Entity
@Table(name = "vacancy_recommendation_task")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class VacancyRecommendationTaskEntity extends BaseEntity {

    @Column(name = "vacancy_id", nullable = false, unique = true)
    private UUID vacancyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VacancyRecommendationTaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private VacancyRecommendationTaskOutcome outcome;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "lease_owner", length = 255)
    private String leaseOwner;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_error_category", length = 40)
    private VacancyRecommendationFailureCategory lastErrorCategory;

    @Column(name = "completed_at")
    private Instant completedAt;
}

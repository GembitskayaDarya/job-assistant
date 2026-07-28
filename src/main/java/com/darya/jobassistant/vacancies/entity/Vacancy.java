package com.darya.jobassistant.vacancies.entity;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.entity.BaseEntity;
import com.darya.jobassistant.vacancyextraction.model.RemotePolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "vacancy")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Vacancy extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column
    private String url;

    /**
     * The {@code VacancyUrlCanonicalizer}-normalized form of {@link #url}, used for
     * deduplication (see {@code uk_vacancy_canonical_url}). Mandatory and globally unique at the
     * database level since Sprint 8 Step 4B2D (migration V13) - every row, including every row
     * that predates Sprint 8 Step 4B1, was backfilled (Step 4B2B) before V13 could be applied.
     * {@code VacancyCreationService} always canonicalizes {@link #url} before building a new
     * {@code Vacancy}, so no application code path constructs one with a null canonical URL
     * either; this {@code nullable = false} only makes that existing invariant visible to
     * Hibernate's own schema validation.
     */
    @Column(name = "canonical_url", nullable = false)
    private String canonicalUrl;

    @Column(length = 300)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "remote_mode", length = 20)
    private RemotePolicy remoteMode;

    @Column(name = "salary_min", precision = 12, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 12, scale = 2)
    private BigDecimal salaryMax;

    @Column
    private String currency;

    @Column(name = "salary_text", length = 200)
    private String salaryText;

    @Column
    private String source;

    @Column(name = "posted_at")
    private LocalDate postedAt;
}

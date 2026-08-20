package com.darya.jobassistant.candidates.entity;

import com.darya.jobassistant.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Sprint 9 Step 1 persistence foundation for Candidate Profile (V16), extended by Step 3 (V17)
 * with {@link #currentCountry}/{@link #relocationAllowed}/{@link #salaryExpectationNote} - still
 * additive and currently unused: {@code ConfigurationCandidateProfileProvider} remains the active
 * runtime source of {@code com.darya.jobassistant.candidates.CandidateProfile}.
 *
 * <p>{@link #profileKey} is the stable, unique business key later steps will look profiles up by
 * (e.g. {@code "primary"}) - {@code id} stays a plain generated surrogate, never hardcoded or
 * treated as the "active profile" marker.
 *
 * <p>{@link CandidateProfileSkillEntity}, {@link CandidateProfileLanguageEntity}, and {@link
 * CandidateProfilePreferenceEntity} reference this entity via a unidirectional {@code @ManyToOne}
 * (no owning collection here), matching how every other parent/child pair in this codebase is
 * modeled (e.g. {@code Vacancy}/{@code JobAnalysisEntity}, {@code Vacancy}/{@code
 * VacancyRecommendationTaskEntity}): the database's {@code ON DELETE CASCADE} (see V16/V17) is
 * solely responsible for removing child rows when a profile is deleted, never JPA-level
 * cascade/orphan-removal.
 */
@Entity
@Table(name = "candidate_profile")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class CandidateProfileEntity extends BaseEntity {

    @Column(name = "profile_key", nullable = false, length = 100)
    private String profileKey;

    @Column(name = "target_role", nullable = false, length = 255)
    private String targetRole;

    @Column(length = 50)
    private String seniority;

    @Column(name = "experience_years", nullable = false)
    private int experienceYears;

    @Column(name = "preferred_company_type", length = 100)
    private String preferredCompanyType;

    @Column(name = "preferred_location", length = 300)
    private String preferredLocation;

    @Column(name = "employment_model", length = 50)
    private String employmentModel;

    @Column(name = "remote_policy", length = 20)
    private String remotePolicy;

    @Column(name = "salary_currency", length = 3)
    private String salaryCurrency;

    @Column(name = "minimum_salary", precision = 12, scale = 2)
    private BigDecimal minimumSalary;

    @Column(name = "current_country", length = 100)
    private String currentCountry;

    @Column(name = "relocation_allowed", nullable = false)
    private boolean relocationAllowed;

    @Column(name = "salary_expectation_note", length = 500)
    private String salaryExpectationNote;

    /** Sprint 11 Step 5: CV header/contact facts - see {@link CandidateProfileEntity}'s javadoc. */
    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(length = 255)
    private String email;

    @Column(length = 50)
    private String phone;

    @Column(name = "linkedin_url", length = 500)
    private String linkedinUrl;

    @Column(name = "cv_location", length = 300)
    private String cvLocation;

    /** Fixed, never-AI-generated CV title - deliberately separate from {@link #targetRole}. */
    @Column(name = "cv_headline", length = 255)
    private String cvHeadline;

    @Version
    @Column(nullable = false)
    private long version;
}

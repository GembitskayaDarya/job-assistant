package com.darya.jobassistant.personalprojects.entity;

import com.darya.jobassistant.candidates.entity.CandidateProfileEntity;
import com.darya.jobassistant.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Sprint 11 Step 5 persistence for the Personal Project aggregate root (V29's {@code
 * personal_project}) - an independent aggregate from {@link CandidateProfileEntity}, {@code
 * @ManyToOne} (not {@code @OneToOne}, unlike Career History's root): one candidate can have many
 * Personal Projects, each its own row, each its own {@link #version} - see {@code
 * personalprojects.aggregate.PersonalProject}'s javadoc for why no wrapper row exists.
 *
 * <p>Unidirectional, matching this codebase's parent/child convention throughout: {@link
 * CandidateProfileEntity} is not modified to add a back-reference. Highlight/technology children
 * are reached only by querying on {@code personal_project_id}, never a {@code @OneToMany}
 * collection here - database {@code ON DELETE CASCADE} (V29) is solely responsible for removing
 * them, never JPA-level cascade/orphan-removal.
 *
 * <p>{@link #version} is bumped via an explicit, unconditional {@code updateVersionIfMatches}
 * modifying query (see {@code PersonalProjectRepository}), not Hibernate's automatic dirty-check
 * increment - a save that only replaces this project's highlights/technologies must still be
 * treated as a real modification of this row for optimistic-locking purposes, the same reasoning
 * {@code CandidateProfileEntity}/{@code CareerHistoryEntity} both already rely on.
 */
@Entity
@Table(name = "personal_project")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PersonalProjectEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_profile_id", nullable = false)
    private CandidateProfileEntity candidateProfile;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 500)
    private String url;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Version
    @Column(nullable = false)
    private long version;
}

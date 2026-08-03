package com.darya.jobassistant.careerhistory.entity;

import com.darya.jobassistant.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Sprint 9 Step 5 persistence for one ordered technology tag under a {@link CareerProjectEntity}
 * (V19). {@link #technologyName} is never lowercased or otherwise normalized, matching {@code
 * CandidateProfileSkillEntity#getName()}'s convention (V16) - "Java", "PostgreSQL", "AWS" keep
 * their meaningful capitalization. No global technology dictionary is introduced; this is a free
 * string scoped to one project. See {@link CareerHistoryEntity}'s javadoc for the shared
 * ownership/cascade rationale.
 */
@Entity
@Table(name = "career_project_technology")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class CareerProjectTechnologyEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_project_id", nullable = false)
    private CareerProjectEntity careerProject;

    @Column(name = "technology_name", nullable = false, length = 150)
    private String technologyName;

    @Column(length = 100)
    private String category;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}

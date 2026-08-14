package com.darya.jobassistant.personalprojects.entity;

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
 * Sprint 11 Step 5 persistence for one technology tag within a {@link PersonalProjectEntity}
 * (V29's {@code personal_project_technology}) - replaced atomically together with the rest of one
 * project's own graph on that project's own save. The database enforces plain, case-sensitive
 * uniqueness of {@link #technologyName} per project; the domain layer ({@code
 * personalprojects.aggregate.PersonalProject}) additionally normalizes before comparing.
 */
@Entity
@Table(name = "personal_project_technology")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PersonalProjectTechnologyEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "personal_project_id", nullable = false)
    private PersonalProjectEntity personalProject;

    @Column(name = "technology_name", nullable = false, length = 150)
    private String technologyName;

    @Column(length = 100)
    private String category;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}

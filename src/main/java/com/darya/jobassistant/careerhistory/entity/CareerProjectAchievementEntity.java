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
 * Sprint 9 Step 5 persistence for one ordered achievement bullet under a {@link
 * CareerProjectEntity} (V19) - see {@link CareerHistoryEntity}'s javadoc for the shared
 * ownership/cascade rationale.
 */
@Entity
@Table(name = "career_project_achievement")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class CareerProjectAchievementEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_project_id", nullable = false)
    private CareerProjectEntity careerProject;

    @Column(name = "achievement_text", nullable = false, length = 2000)
    private String achievementText;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}

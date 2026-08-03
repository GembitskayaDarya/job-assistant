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
 * CareerPositionEntity} (V19) - see {@link CareerHistoryEntity}'s javadoc for the shared
 * ownership/cascade rationale. Qualitative achievements (no numeric metric) are valid; nothing
 * here requires one.
 */
@Entity
@Table(name = "career_position_achievement")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class CareerPositionAchievementEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_position_id", nullable = false)
    private CareerPositionEntity careerPosition;

    @Column(name = "achievement_text", nullable = false, length = 2000)
    private String achievementText;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}

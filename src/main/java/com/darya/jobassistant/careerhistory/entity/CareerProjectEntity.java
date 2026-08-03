package com.darya.jobassistant.careerhistory.entity;

import com.darya.jobassistant.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Sprint 9 Step 5 persistence for one project within a {@link CareerPositionEntity} (V19). {@link
 * #startDate}/{@link #endDate} are both nullable, unlike the owning position's start date - a
 * project's timeline is frequently vaguer than the position that contains it - and are not
 * database-constrained to fall inside the position's own dates; that cross-row invariant is later
 * domain/application validation work, not this persistence foundation. See {@link
 * CareerHistoryEntity}'s javadoc for the shared ownership/cascade rationale.
 */
@Entity
@Table(name = "career_project")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class CareerProjectEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_position_id", nullable = false)
    private CareerPositionEntity careerPosition;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}

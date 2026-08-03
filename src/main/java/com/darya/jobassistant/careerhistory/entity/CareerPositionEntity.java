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
 * Sprint 9 Step 5 persistence for one position within a {@link CareerCompanyEntity} (V19). {@link
 * #displayOrder} is explicit business ordering data (resume order), never inferred from physical
 * row order - see {@link CareerHistoryEntity}'s javadoc for the shared ownership/cascade
 * rationale.
 */
@Entity
@Table(name = "career_position")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class CareerPositionEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_company_id", nullable = false)
    private CareerCompanyEntity careerCompany;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "employment_type", length = 50)
    private String employmentType;

    @Column(length = 300)
    private String location;

    @Column(name = "work_arrangement", length = 50)
    private String workArrangement;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    // Not "current_role": that is a reserved SQL/PostgreSQL keyword (CURRENT_ROLE) - see V19.
    @Column(name = "is_current_role", nullable = false)
    private boolean currentRole;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}

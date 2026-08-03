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
 * Sprint 9 Step 5 persistence for one company within a {@link CareerHistoryEntity} (V19).
 * Unidirectional {@code @ManyToOne} to the root, matching every child entity in this package -
 * see {@link CareerHistoryEntity}'s javadoc for the shared ownership/cascade rationale.
 *
 * <p>{@link #displayOrder} (V20, Sprint 9 Step 5 correction) is explicit, persisted business
 * ordering data - dates alone cannot order companies (positions may overlap, dates may be
 * incomplete or identical across companies), and a later import/export or AI/CV rendering step
 * needs a deterministic, explicitly-chosen order. Never derived from {@link #name} or any child
 * position's dates, and never a JPA {@code @OrderColumn} (which would tie ordering to collection
 * index management this codebase's unidirectional child-repository style doesn't use at all -
 * see {@link CareerHistoryEntity}'s javadoc on why there is no owning collection here).
 */
@Entity
@Table(name = "career_company")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class CareerCompanyEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "career_history_id", nullable = false)
    private CareerHistoryEntity careerHistory;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 500)
    private String website;

    @Column(length = 150)
    private String industry;

    @Column(length = 300)
    private String location;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}

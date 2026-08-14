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
 * Sprint 11 Step 5 persistence for one highlight bullet within a {@link PersonalProjectEntity}
 * (V29's {@code personal_project_highlight}) - replaced atomically together with the rest of one
 * project's own graph on that project's own save, never touching another project's rows.
 */
@Entity
@Table(name = "personal_project_highlight")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PersonalProjectHighlightEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "personal_project_id", nullable = false)
    private PersonalProjectEntity personalProject;

    @Column(name = "highlight_text", nullable = false, length = 2000)
    private String highlightText;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}

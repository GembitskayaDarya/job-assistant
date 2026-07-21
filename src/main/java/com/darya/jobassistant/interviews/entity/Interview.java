package com.darya.jobassistant.interviews.entity;

import com.darya.jobassistant.entity.BaseEntity;
import com.darya.jobassistant.tracking.entity.Application;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "interview")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Interview extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private InterviewType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private InterviewStatus status;

    @Column(columnDefinition = "TEXT")
    private String notes;
}

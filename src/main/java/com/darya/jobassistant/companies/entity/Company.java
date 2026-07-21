package com.darya.jobassistant.companies.entity;

import com.darya.jobassistant.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "company")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Company extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column
    private String website;

    @Column(columnDefinition = "TEXT")
    private String notes;
}

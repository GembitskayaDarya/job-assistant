package com.darya.jobassistant.careerhistory.repository;

import com.darya.jobassistant.careerhistory.entity.CareerProjectTechnologyEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Internal Spring Data repository for {@link CareerProjectTechnologyEntity} - Sprint 9 Step 5
 * persistence foundation only.
 */
public interface CareerProjectTechnologyRepository extends JpaRepository<CareerProjectTechnologyEntity, UUID> {

    List<CareerProjectTechnologyEntity> findAllByCareerProjectIdOrderByDisplayOrderAsc(UUID careerProjectId);
}

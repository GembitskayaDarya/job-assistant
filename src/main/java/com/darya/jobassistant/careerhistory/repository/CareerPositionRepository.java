package com.darya.jobassistant.careerhistory.repository;

import com.darya.jobassistant.careerhistory.entity.CareerPositionEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Internal Spring Data repository for {@link CareerPositionEntity} - Sprint 9 Step 5 persistence
 * foundation only. Never relies on physical row order - {@code display_order} is explicit
 * business data (V19).
 */
public interface CareerPositionRepository extends JpaRepository<CareerPositionEntity, UUID> {

    List<CareerPositionEntity> findAllByCareerCompanyIdOrderByDisplayOrderAsc(UUID careerCompanyId);
}

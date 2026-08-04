package com.darya.jobassistant.careerhistory.repository;

import com.darya.jobassistant.careerhistory.entity.CareerProjectEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Internal Spring Data repository for {@link CareerProjectEntity} - Sprint 9 Step 5 persistence
 * foundation, extended by Step 6 with {@link #findAllByCareerPositionIdInOrderByDisplayOrderAsc}.
 */
public interface CareerProjectRepository extends JpaRepository<CareerProjectEntity, UUID> {

    List<CareerProjectEntity> findAllByCareerPositionIdOrderByDisplayOrderAsc(UUID careerPositionId);

    List<CareerProjectEntity> findAllByCareerPositionIdInOrderByDisplayOrderAsc(Collection<UUID> careerPositionIds);
}

package com.darya.jobassistant.careerhistory.repository;

import com.darya.jobassistant.careerhistory.entity.CareerProjectResponsibilityEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Internal Spring Data repository for {@link CareerProjectResponsibilityEntity} - Sprint 9 Step 5
 * persistence foundation, extended by Step 6 with {@link #findAllByCareerProjectIdInOrderByDisplayOrderAsc}.
 */
public interface CareerProjectResponsibilityRepository extends JpaRepository<CareerProjectResponsibilityEntity, UUID> {

    List<CareerProjectResponsibilityEntity> findAllByCareerProjectIdOrderByDisplayOrderAsc(UUID careerProjectId);

    List<CareerProjectResponsibilityEntity> findAllByCareerProjectIdInOrderByDisplayOrderAsc(Collection<UUID> careerProjectIds);
}

package com.darya.jobassistant.careerhistory.repository;

import com.darya.jobassistant.careerhistory.entity.CareerProjectAchievementEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Internal Spring Data repository for {@link CareerProjectAchievementEntity} - Sprint 9 Step 5
 * persistence foundation, extended by Step 6 with {@link #findAllByCareerProjectIdInOrderByDisplayOrderAsc}.
 */
public interface CareerProjectAchievementRepository extends JpaRepository<CareerProjectAchievementEntity, UUID> {

    List<CareerProjectAchievementEntity> findAllByCareerProjectIdOrderByDisplayOrderAsc(UUID careerProjectId);

    List<CareerProjectAchievementEntity> findAllByCareerProjectIdInOrderByDisplayOrderAsc(Collection<UUID> careerProjectIds);
}

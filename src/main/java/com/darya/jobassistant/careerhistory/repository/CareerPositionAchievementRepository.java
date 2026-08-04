package com.darya.jobassistant.careerhistory.repository;

import com.darya.jobassistant.careerhistory.entity.CareerPositionAchievementEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Internal Spring Data repository for {@link CareerPositionAchievementEntity} - Sprint 9 Step 5
 * persistence foundation, extended by Step 6 with {@link #findAllByCareerPositionIdInOrderByDisplayOrderAsc}.
 */
public interface CareerPositionAchievementRepository extends JpaRepository<CareerPositionAchievementEntity, UUID> {

    List<CareerPositionAchievementEntity> findAllByCareerPositionIdOrderByDisplayOrderAsc(UUID careerPositionId);

    List<CareerPositionAchievementEntity> findAllByCareerPositionIdInOrderByDisplayOrderAsc(Collection<UUID> careerPositionIds);
}

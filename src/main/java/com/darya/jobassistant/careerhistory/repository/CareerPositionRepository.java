package com.darya.jobassistant.careerhistory.repository;

import com.darya.jobassistant.careerhistory.entity.CareerPositionEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Internal Spring Data repository for {@link CareerPositionEntity} - Sprint 9 Step 5 persistence
 * foundation, extended by Step 6 with {@link #findAllByCareerCompanyIdInOrderByDisplayOrderAsc}.
 * Never relies on physical row order - {@code display_order} is explicit business data (V19).
 */
public interface CareerPositionRepository extends JpaRepository<CareerPositionEntity, UUID> {

    List<CareerPositionEntity> findAllByCareerCompanyIdOrderByDisplayOrderAsc(UUID careerCompanyId);

    /**
     * Sprint 9 Step 6: loads positions for every company in {@code careerCompanyIds} in one
     * query - {@code CareerHistoryRepositoryAdapter}'s level-based graph loading uses this
     * instead of one {@link #findAllByCareerCompanyIdOrderByDisplayOrderAsc} call per company, so
     * the total query count stays bounded by graph levels rather than graph size.
     */
    List<CareerPositionEntity> findAllByCareerCompanyIdInOrderByDisplayOrderAsc(Collection<UUID> careerCompanyIds);
}

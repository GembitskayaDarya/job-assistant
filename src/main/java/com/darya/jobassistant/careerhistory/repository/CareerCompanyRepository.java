package com.darya.jobassistant.careerhistory.repository;

import com.darya.jobassistant.careerhistory.entity.CareerCompanyEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Internal Spring Data repository for {@link CareerCompanyEntity} - Sprint 9 Step 5 persistence
 * foundation only. Companies within one Career History are explicitly ordered via {@code
 * display_order} (V20, Sprint 9 Step 5 correction) - never physical row order.
 */
public interface CareerCompanyRepository extends JpaRepository<CareerCompanyEntity, UUID> {

    List<CareerCompanyEntity> findAllByCareerHistoryIdOrderByDisplayOrderAsc(UUID careerHistoryId);
}

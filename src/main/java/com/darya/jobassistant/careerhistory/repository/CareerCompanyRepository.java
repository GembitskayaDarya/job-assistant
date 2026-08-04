package com.darya.jobassistant.careerhistory.repository;

import com.darya.jobassistant.careerhistory.entity.CareerCompanyEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Internal Spring Data repository for {@link CareerCompanyEntity} - Sprint 9 Step 5 persistence
 * foundation, extended by Step 6's correction with {@link #deleteAllByCareerHistoryId}. Companies
 * within one Career History are explicitly ordered via {@code display_order} (V20) - never
 * physical row order.
 */
public interface CareerCompanyRepository extends JpaRepository<CareerCompanyEntity, UUID> {

    List<CareerCompanyEntity> findAllByCareerHistoryIdOrderByDisplayOrderAsc(UUID careerHistoryId);

    /**
     * Deletes only the companies belonging to {@code careerHistoryId} - database {@code ON DELETE
     * CASCADE} (V19) removes their complete descendant graph. Deliberately scoped by a {@code
     * WHERE} clause, never the table-wide, no-argument {@code deleteAll()} - {@code
     * CareerHistoryRepositoryAdapter#deleteExistingGraph} relies on this to guarantee replacing
     * one Career History can never touch another's rows, proven by {@code
     * CareerHistoryRepositoryAdapterIsolationTest}.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CareerCompanyEntity company where company.careerHistory.id = :careerHistoryId")
    int deleteAllByCareerHistoryId(@Param("careerHistoryId") UUID careerHistoryId);
}

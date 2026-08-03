package com.darya.jobassistant.careerhistory.repository;

import com.darya.jobassistant.careerhistory.entity.CareerHistoryEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Internal Spring Data repository for {@link CareerHistoryEntity} - Sprint 9 Step 5 persistence
 * foundation only, matching {@code CandidateProfileRepository}'s Step 1 convention: not yet
 * called by any application service. {@code CareerHistoryRepositoryPort} is Step 6 work.
 */
public interface CareerHistoryRepository extends JpaRepository<CareerHistoryEntity, UUID> {

    Optional<CareerHistoryEntity> findByCandidateProfileId(UUID candidateProfileId);
}

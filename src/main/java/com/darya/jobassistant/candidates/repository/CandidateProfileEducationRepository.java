package com.darya.jobassistant.candidates.repository;

import com.darya.jobassistant.candidates.entity.CandidateProfileEducationEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Internal Spring Data repository for {@link CandidateProfileEducationEntity} - Sprint 11 Step 5.
 * {@link #findByCandidateProfileIdOrderByDisplayOrderAscIdAsc} matches {@code
 * CandidateProfilePreferenceRepository}'s ordered-finder convention, needed for deterministic CV
 * presentation order.
 */
public interface CandidateProfileEducationRepository extends JpaRepository<CandidateProfileEducationEntity, UUID> {

    List<CandidateProfileEducationEntity> findByCandidateProfileId(UUID candidateProfileId);

    List<CandidateProfileEducationEntity> findByCandidateProfileIdOrderByDisplayOrderAscIdAsc(UUID candidateProfileId);
}

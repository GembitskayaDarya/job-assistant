package com.darya.jobassistant.candidates.repository;

import com.darya.jobassistant.candidates.entity.CandidateProfileEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Internal Spring Data repository for {@link CandidateProfileEntity} - Sprint 9 Step 1 persistence
 * foundation only. Not yet called by any application service; the runtime Candidate Profile
 * provider remains {@code ConfigurationCandidateProfileProvider}. An application-level repository
 * port over this, if one turns out to be needed, is later Sprint 9 work.
 */
public interface CandidateProfileRepository extends JpaRepository<CandidateProfileEntity, UUID> {

    Optional<CandidateProfileEntity> findByProfileKey(String profileKey);

    boolean existsByProfileKey(String profileKey);
}

package com.darya.jobassistant.candidates.repository;

import com.darya.jobassistant.candidates.entity.CandidateProfileLanguageEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Internal Spring Data repository for {@link CandidateProfileLanguageEntity} - Sprint 9 Step 1
 * persistence foundation only, needed because languages are persisted directly (this entity has
 * no owning collection on {@code CandidateProfileEntity}, see its javadoc). Not yet called by any
 * application service.
 */
public interface CandidateProfileLanguageRepository extends JpaRepository<CandidateProfileLanguageEntity, UUID> {

    List<CandidateProfileLanguageEntity> findByCandidateProfileId(UUID candidateProfileId);

    /** Sprint 11 Step 5: deterministic CV presentation order (V28's {@code display_order}). */
    List<CandidateProfileLanguageEntity> findByCandidateProfileIdOrderByDisplayOrderAscIdAsc(UUID candidateProfileId);
}

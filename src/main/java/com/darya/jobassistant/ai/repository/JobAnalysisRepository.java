package com.darya.jobassistant.ai.repository;

import com.darya.jobassistant.ai.entity.JobAnalysisEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobAnalysisRepository extends JpaRepository<JobAnalysisEntity, UUID> {

    Optional<JobAnalysisEntity> findByVacancyId(UUID vacancyId);

    List<JobAnalysisEntity> findByVacancyIdIn(Collection<UUID> vacancyIds);
}

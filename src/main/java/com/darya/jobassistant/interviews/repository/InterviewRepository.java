package com.darya.jobassistant.interviews.repository;

import com.darya.jobassistant.interviews.entity.Interview;
import com.darya.jobassistant.interviews.entity.InterviewStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewRepository extends JpaRepository<Interview, UUID> {

    List<Interview> findByApplicationId(UUID applicationId);

    List<Interview> findByStatus(InterviewStatus status);

    List<Interview> findByScheduledAtBetween(Instant from, Instant to);
}

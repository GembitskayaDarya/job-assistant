package com.darya.jobassistant.interviews.repository;

import com.darya.jobassistant.interviews.entity.Interview;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewRepository extends JpaRepository<Interview, UUID> {
}

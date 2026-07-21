package com.darya.jobassistant.repository;

import com.darya.jobassistant.entity.JobApplication;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    List<JobApplication> findByTelegramChatId(Long telegramChatId);
}
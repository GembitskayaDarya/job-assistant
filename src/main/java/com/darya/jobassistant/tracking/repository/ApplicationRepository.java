package com.darya.jobassistant.tracking.repository;

import com.darya.jobassistant.tracking.entity.Application;
import com.darya.jobassistant.tracking.entity.ApplicationStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    List<Application> findByTelegramUserChatId(Long chatId);

    List<Application> findByCompanyId(UUID companyId);

    List<Application> findByStatus(ApplicationStatus status);
}

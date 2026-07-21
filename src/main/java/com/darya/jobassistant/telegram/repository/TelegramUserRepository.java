package com.darya.jobassistant.telegram.repository;

import com.darya.jobassistant.telegram.entity.TelegramUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramUserRepository extends JpaRepository<TelegramUser, UUID> {

    Optional<TelegramUser> findByChatId(Long chatId);
}

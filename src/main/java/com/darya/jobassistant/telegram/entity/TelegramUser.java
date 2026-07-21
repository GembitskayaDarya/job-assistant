package com.darya.jobassistant.telegram.entity;

import com.darya.jobassistant.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "telegram_user")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class TelegramUser extends BaseEntity {

    @Column(name = "chat_id", nullable = false, unique = true)
    private Long chatId;

    @Column(name = "username")
    private String username;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;
}

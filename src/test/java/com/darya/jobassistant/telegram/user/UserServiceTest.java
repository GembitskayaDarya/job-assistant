package com.darya.jobassistant.telegram.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, transactionManager);
    }

    @Test
    void registerOrUpdate_registersNewUserWhenNoneExists() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        when(userRepository.findByTelegramId(111L)).thenReturn(Optional.empty());
        User saved = User.builder().id(UUID.randomUUID()).telegramId(111L).username("darya").build();
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(saved);

        User result = userService.registerOrUpdate(111L, "darya", "Darya", "H");

        assertThat(result).isSameAs(saved);
    }

    @Test
    void registerOrUpdate_updatesUsernameWhenChanged() {
        User existing = User.builder().id(UUID.randomUUID()).telegramId(111L).username("old_name").build();
        when(userRepository.findByTelegramId(111L)).thenReturn(Optional.of(existing));

        User result = userService.registerOrUpdate(111L, "new_name", "Darya", "H");

        assertThat(result.getUsername()).isEqualTo("new_name");
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerOrUpdate_returnsExistingUserUnchangedWhenUsernameMatches() {
        User existing = User.builder().id(UUID.randomUUID()).telegramId(111L).username("darya").build();
        when(userRepository.findByTelegramId(111L)).thenReturn(Optional.of(existing));

        User result = userService.registerOrUpdate(111L, "darya", "Darya", "H");

        assertThat(result).isSameAs(existing);
    }

    @Test
    void registerOrUpdate_recoversWhenConcurrentStartWinsTheRace() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        User winner = User.builder().id(UUID.randomUUID()).telegramId(111L).username("darya").build();
        when(userRepository.findByTelegramId(111L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("uk_users_telegram_id"));

        User result = userService.registerOrUpdate(111L, "darya", "Darya", "H");

        assertThat(result).isSameAs(winner);
        verify(transactionManager).rollback(transactionStatus);
    }
}

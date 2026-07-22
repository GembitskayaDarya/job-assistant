package com.darya.jobassistant.telegram.user;

import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final TransactionTemplate requiresNewTransaction;

    public UserService(UserRepository userRepository, PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public User registerOrUpdate(Long telegramId, String username, String firstName, String lastName) {
        return userRepository.findByTelegramId(telegramId)
                .map(existing -> applyUsernameChange(existing, username))
                .orElseGet(() -> registerOrRecoverFromRace(telegramId, username, firstName, lastName));
    }

    private User registerOrRecoverFromRace(Long telegramId, String username, String firstName, String lastName) {
        try {
            // Runs in its own physical transaction: if the unique constraint on telegram_id
            // fires (another /start for the same user committed first), only this insert
            // rolls back — the caller's transaction is untouched and can still read the
            // winner's row below. A plain try/catch in one transaction wouldn't work here,
            // since Postgres aborts the whole transaction on a constraint violation.
            return requiresNewTransaction.execute(status -> userRepository.saveAndFlush(User.builder()
                    .telegramId(telegramId)
                    .username(username)
                    .firstName(firstName)
                    .lastName(lastName)
                    .build()));
        } catch (DataIntegrityViolationException lostRegistrationRace) {
            return userRepository.findByTelegramId(telegramId)
                    .orElseThrow(() -> lostRegistrationRace);
        }
    }

    private User applyUsernameChange(User user, String username) {
        if (!Objects.equals(user.getUsername(), username)) {
            user.setUsername(username);
        }
        return user;
    }
}

package com.darya.jobassistant.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Smallest configuration needed for ShedLock's cluster-wide distributed locking: a {@link
 * LockProvider} backed by this application's existing {@link DataSource} - never a second
 * DataSource - and {@link EnableSchedulerLock} in its default {@code PROXY_METHOD} mode (the
 * modern, supported AOP-around-the-annotated-method approach; the older {@code PROXY_SCHEDULER}
 * mode, which wraps the {@code TaskScheduler} itself, is deprecated and deliberately not selected
 * here).
 *
 * <p>Deliberately unconditional: this bean is inert until some {@code @SchedulerLock}-annotated
 * method actually exists and is invoked, which today only happens when {@code
 * job-discovery.scheduler.enabled=true} activates {@code JobDiscoveryScheduler}. Gating this
 * configuration itself on that same property would add a second activation switch for no
 * behavioral benefit, since {@link LockProvider} performs no I/O on its own until asked to acquire
 * a named lock.
 *
 * <p>{@link JdbcTemplateLockProvider.Configuration#usingDbTime()} makes lock expiry compare against
 * PostgreSQL's own clock ({@code now()}), not this JVM's system time - required so lock validity is
 * consistent across application instances even if their local clocks drift.
 *
 * <p>{@code defaultLockAtMostFor} is a mandatory {@link EnableSchedulerLock} attribute with no
 * built-in default - it is never actually applied here, since the only current {@code
 * @SchedulerLock} usage ({@code JobDiscoveryScheduler}) always sets its own {@code lockAtMostFor}
 * explicitly from {@code job-discovery.scheduler.lock-at-most-for}.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }
}

package com.darya.jobassistant.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.config.ShedLockConfig;
import com.darya.jobassistant.jobdiscovery.JobDiscoveryRunResult;
import com.darya.jobassistant.jobdiscovery.JobDiscoveryService;
import com.darya.jobassistant.jobdiscovery.budget.JobDiscoveryBudgetDecision;
import com.darya.jobassistant.jobdiscovery.budget.JobDiscoveryBudgetStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves cluster-wide, PostgreSQL-backed non-overlapping execution end-to-end: two independent
 * Spring contexts (each with its own {@link DataSource}, its own {@code ShedLockConfig}-backed
 * {@code LockProvider}, and its own {@link JobDiscoveryScheduler} bean, wired exactly like
 * production) simulate two application instances sharing one PostgreSQL database. {@code
 * @SchedulerLock}'s AOP proxy is real here - {@code runScheduledDiscovery()} is invoked directly
 * on the Spring-managed (proxied) bean, never on a raw instance, so {@link
 * net.javacrumbs.shedlock.core.LockAssert#assertLocked()} is exercised for real, not bypassed via
 * {@code LockAssert.TestHelper}.
 *
 * <p>Each "node" gets its own {@link DataSource} (a separate JDBC connection), deliberately -
 * sharing one {@code DataSource}/connection pool between two contexts would not prove anything
 * about cross-process coordination, only single-connection serialization.
 *
 * <p>A {@link CountDownLatch} pair holds the winning node's execution open exactly long enough for
 * the second node's attempt to run against the now-already-locked row - the only test hook used;
 * no production code is touched to make this observable.
 */
@Testcontainers
class JobDiscoverySchedulerConcurrencyTest {

    private static final String LOCK_NAME = "jobDiscoveryDailyRun";

    @Container
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private final ExecutorService nodeAExecutor = Executors.newSingleThreadExecutor();
    private AnnotationConfigApplicationContext contextA;
    private AnnotationConfigApplicationContext contextB;

    @AfterEach
    void tearDown() {
        nodeAExecutor.shutdownNow();
        if (contextA != null) {
            contextA.close();
        }
        if (contextB != null) {
            contextB.close();
        }
    }

    @Test
    void concurrentInvocation_secondNodeIsSkipped_exactlyOneRunAcrossBothNodes() throws Exception {
        migrateSchema();

        JobDiscoveryService serviceA = mock(JobDiscoveryService.class);
        JobDiscoveryService serviceB = mock(JobDiscoveryService.class);
        CountDownLatch nodeAEnteredLock = new CountDownLatch(1);
        CountDownLatch releaseNodeA = new CountDownLatch(1);
        when(serviceA.runDiscovery()).thenAnswer(invocation -> {
            nodeAEnteredLock.countDown();
            assertThat(releaseNodeA.await(10, TimeUnit.SECONDS)).isTrue();
            return allowedResult();
        });

        contextA = newNodeContext(serviceA);
        contextB = newNodeContext(serviceB);

        Future<?> nodeARun = nodeAExecutor.submit(
                () -> contextA.getBean(JobDiscoveryScheduler.class).runScheduledDiscovery());

        assertThat(nodeAEnteredLock.await(10, TimeUnit.SECONDS)).isTrue();

        // Node B attempts while node A still holds the lock in Postgres - this must return
        // immediately (no wait, no retry), performing zero JobDiscoveryService work.
        Instant beforeNodeBAttempt = Instant.now();
        contextB.getBean(JobDiscoveryScheduler.class).runScheduledDiscovery();
        Duration nodeBElapsed = Duration.between(beforeNodeBAttempt, Instant.now());

        releaseNodeA.countDown();
        nodeARun.get(10, TimeUnit.SECONDS);

        verify(serviceA, times(1)).runDiscovery();
        verify(serviceB, never()).runDiscovery();
        assertThat(nodeBElapsed).isLessThan(Duration.ofSeconds(5)); // no lock wait/retry, no deadlock

        assertSingleLockRowWithExpectedName();
    }

    @Test
    void afterNormalCompletion_laterInvocationCanAcquireTheLock() throws Exception {
        migrateSchema();
        JobDiscoveryService service = mock(JobDiscoveryService.class);
        when(service.runDiscovery()).thenReturn(allowedResult());
        contextA = newNodeContext(service);
        JobDiscoveryScheduler scheduler = contextA.getBean(JobDiscoveryScheduler.class);

        scheduler.runScheduledDiscovery();
        scheduler.runScheduledDiscovery();

        verify(service, times(2)).runDiscovery();
        assertSingleLockRowWithExpectedName();
    }

    @Test
    void afterHandledRuntimeFailure_laterInvocationCanAcquireTheLock() throws Exception {
        migrateSchema();
        JobDiscoveryService service = mock(JobDiscoveryService.class);
        when(service.runDiscovery())
                .thenThrow(new RuntimeException("handled failure"))
                .thenReturn(allowedResult());
        contextA = newNodeContext(service);
        JobDiscoveryScheduler scheduler = contextA.getBean(JobDiscoveryScheduler.class);

        scheduler.runScheduledDiscovery();
        scheduler.runScheduledDiscovery();

        verify(service, times(2)).runDiscovery();
    }

    @Test
    void manuallyPreparedUnexpiredLock_causesExecutionToBeSkipped() throws Exception {
        migrateSchema();
        insertLockRow(Duration.ofHours(1)); // lock_until = now + 1h, still valid
        JobDiscoveryService service = mock(JobDiscoveryService.class);
        contextA = newNodeContext(service);

        contextA.getBean(JobDiscoveryScheduler.class).runScheduledDiscovery();

        verify(service, never()).runDiscovery();
        assertSingleLockRowWithExpectedName();
    }

    @Test
    void expiredLock_permitsExecution() throws Exception {
        migrateSchema();
        insertLockRow(Duration.ofHours(-1)); // lock_until = utc now - 1h, already expired
        JobDiscoveryService service = mock(JobDiscoveryService.class);
        when(service.runDiscovery()).thenReturn(allowedResult());
        contextA = newNodeContext(service);

        contextA.getBean(JobDiscoveryScheduler.class).runScheduledDiscovery();

        verify(service, times(1)).runDiscovery();
        assertSingleLockRowWithExpectedName();
    }

    @Test
    void lockAcquisition_locksAtAndLockUntilAreCloseToDatabaseTime() throws Exception {
        migrateSchema();
        JobDiscoveryService service = mock(JobDiscoveryService.class);
        when(service.runDiscovery()).thenReturn(allowedResult());
        contextA = newNodeContext(service);

        contextA.getBean(JobDiscoveryScheduler.class).runScheduledDiscovery();

        // The reference-vs-locked_at difference is computed entirely server-side, using the exact
        // same timezone('utc', CURRENT_TIMESTAMP) expression ShedLock's own PostgreSQL db-time
        // statements source uses internally (confirmed by inspecting
        // PostgresSqlServerTimeStatementsSource) - never plain now()/CURRENT_TIMESTAMP, which
        // reflects the session's own zone (e.g. Europe/Warsaw, UTC+1/+2) and would compare against
        // the wrong basis; and never a client-side java.sql.Timestamp round trip, which would
        // silently reinterpret the naive value using the JVM's default zone.
        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT EXTRACT(EPOCH FROM (timezone('utc', CURRENT_TIMESTAMP) - locked_at)) AS diff_seconds "
                                + "FROM shedlock WHERE name = '" + LOCK_NAME + "'")) {
            assertThat(resultSet.next()).isTrue();
            double diffSeconds = resultSet.getDouble("diff_seconds");
            // Configured via JdbcTemplateLockProvider.Configuration.usingDbTime(): locked_at is
            // computed by PostgreSQL's own clock, not this JVM's, so it must be close to a
            // freshly-queried db-side reference now - a generous tolerance absorbs query latency.
            assertThat(Math.abs(diffSeconds)).isLessThan(10.0);
        }
    }

    private JobDiscoveryRunResult allowedResult() {
        JobDiscoveryBudgetDecision allowed = new JobDiscoveryBudgetDecision(JobDiscoveryBudgetStatus.ALLOWED, true,
                6, 5, 11, 800, 200, 15,
                1000L, 1000L, 0L, Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-31T23:59:59Z"), null);
        Instant now = Instant.now();
        return new JobDiscoveryRunResult(
                now, now, Duration.ZERO,
                0, 0, 0,
                0, 0, 0,
                0, 0,
                0, 0, 0,
                0, 0, 0,
                0, 0, 0, 0,
                false, false, false, false,
                List.of(), List.of(), 0,
                allowed);
    }

    private void assertSingleLockRowWithExpectedName() throws Exception {
        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT name FROM shedlock")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("name")).isEqualTo(LOCK_NAME);
            assertThat(resultSet.next()).isFalse();
        }
    }

    private void insertLockRow(Duration untilFromNow) throws Exception {
        // Computed entirely server-side, against the exact same timezone('utc', CURRENT_TIMESTAMP)
        // basis ShedLock's own PostgreSQL db-time statements source uses (confirmed by inspecting
        // PostgresSqlServerTimeStatementsSource) - plain now()/CURRENT_TIMESTAMP reflects the
        // session's own zone (e.g. Europe/Warsaw), which would silently stage a row that looks
        // "expired" or "not yet expired" from this session's perspective but the opposite from
        // ShedLock's actual UTC-normalized comparison basis.
        try (Connection connection = jdbcConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO shedlock (name, lock_until, locked_at, locked_by)
                        VALUES (?, timezone('utc', CURRENT_TIMESTAMP) + (? || ' seconds')::interval,
                                timezone('utc', CURRENT_TIMESTAMP), 'manual-test')
                        """)) {
            statement.setString(1, LOCK_NAME);
            statement.setString(2, String.valueOf(untilFromNow.getSeconds()));
            statement.execute();
        }
    }

    private void migrateSchema() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private Connection jdbcConnection() throws Exception {
        return java.sql.DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private DataSource newDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        return dataSource;
    }

    private AnnotationConfigApplicationContext newNodeContext(JobDiscoveryService jobDiscoveryService) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("shedlock-test", Map.of(
                "job-discovery.scheduler.enabled", "true",
                "job-discovery.scheduler.lock-at-most-for", "2h",
                "job-discovery.scheduler.lock-at-least-for", "0s"
        )));
        context.getBeanFactory().registerSingleton("dataSource", newDataSource());
        context.getBeanFactory().registerSingleton("jobDiscoveryService", jobDiscoveryService);
        context.register(ShedLockConfig.class, JobDiscoveryScheduler.class);
        context.refresh();
        return context;
    }
}

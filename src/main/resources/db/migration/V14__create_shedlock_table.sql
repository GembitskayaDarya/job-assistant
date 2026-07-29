-- Sprint 8 Step 9: the standard ShedLock PostgreSQL lock table (JdbcTemplateLockProvider), used to
-- coordinate the daily job-discovery scheduler across every application instance sharing this
-- database. No initial row is required or inserted here - ShedLock creates and updates the row for
-- a given lock name (e.g. "jobDiscoveryDailyRun") itself on first acquisition. This table holds no
-- application secrets and has no foreign key to any application table - it is infrastructure for
-- distributed scheduling only, unrelated to vacancy/company/user data.

CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);

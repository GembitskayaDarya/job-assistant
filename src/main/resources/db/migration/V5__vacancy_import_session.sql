CREATE TABLE vacancy_import_session (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    telegram_chat_id  BIGINT       NOT NULL,
    telegram_user_id  BIGINT       NOT NULL,
    state             VARCHAR(30)  NOT NULL,
    source_url        VARCHAR(1000),
    raw_description   TEXT,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now(),
    expires_at        TIMESTAMP    NOT NULL
);

-- Only one *active* (non-terminal) import session may exist per Telegram chat+user at a time.
-- Terminal sessions (COMPLETED/CANCELLED/FAILED/EXPIRED) are historical records and must not
-- block starting a new import, so a plain UNIQUE constraint on
-- (telegram_chat_id, telegram_user_id) would be wrong - only the row's *state* determines
-- whether it is still in flight. A partial unique index scoped to the active states is the
-- natural way to express that in PostgreSQL (same technique as uk_vacancy_url in
-- V2__vacancy_url_unique.sql), and it does the concurrency-safety job a check-then-insert from
-- application code cannot: a concurrent second INSERT for the same chat+user is rejected
-- outright by the database rather than depending on an earlier SELECT that can race. The
-- friendly pre-check some future step adds on top is only a UX nicety - this index is the
-- actual guarantee.
CREATE UNIQUE INDEX uk_vacancy_import_session_active_chat_user
    ON vacancy_import_session (telegram_chat_id, telegram_user_id)
    WHERE state IN ('WAITING_FOR_URL', 'WAITING_FOR_DESCRIPTION', 'EXTRACTING', 'WAITING_FOR_CONFIRMATION');

CREATE INDEX idx_vacancy_import_session_expires_at ON vacancy_import_session (expires_at);

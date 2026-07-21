CREATE TABLE job_applications (
    id                BIGSERIAL PRIMARY KEY,
    company           VARCHAR(255) NOT NULL,
    position          VARCHAR(255) NOT NULL,
    status            VARCHAR(50)  NOT NULL,
    applied_date      DATE         NOT NULL,
    telegram_chat_id  BIGINT,
    notes             TEXT,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_job_applications_telegram_chat_id ON job_applications (telegram_chat_id);

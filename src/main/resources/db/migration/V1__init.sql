CREATE TABLE company (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    website     VARCHAR(500),
    notes       TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    telegram_id BIGINT       NOT NULL,
    username    VARCHAR(255),
    first_name  VARCHAR(255),
    last_name   VARCHAR(255),
    language    VARCHAR(10),
    timezone    VARCHAR(50),
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_users_telegram_id UNIQUE (telegram_id)
);

CREATE TABLE vacancy (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id   UUID         NOT NULL REFERENCES company (id),
    title        VARCHAR(255) NOT NULL,
    description  TEXT,
    url          VARCHAR(1000),
    salary_min   NUMERIC(12, 2),
    salary_max   NUMERIC(12, 2),
    currency     VARCHAR(10),
    source       VARCHAR(100),
    posted_at    DATE,
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_vacancy_company_id ON vacancy (company_id);

CREATE TABLE application (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id        UUID         NOT NULL REFERENCES company (id),
    vacancy_id        UUID         REFERENCES vacancy (id),
    telegram_user_id  UUID         REFERENCES users (id),
    status            VARCHAR(50)  NOT NULL,
    applied_date      DATE         NOT NULL,
    notes             TEXT,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_application_company_id ON application (company_id);
CREATE INDEX idx_application_vacancy_id ON application (vacancy_id);
CREATE INDEX idx_application_telegram_user_id ON application (telegram_user_id);

CREATE TABLE interview (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID         NOT NULL REFERENCES application (id),
    scheduled_at    TIMESTAMP    NOT NULL,
    type            VARCHAR(50)  NOT NULL,
    status          VARCHAR(50)  NOT NULL,
    notes           TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_interview_application_id ON interview (application_id);

CREATE TABLE notification (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    telegram_user_id  UUID         NOT NULL REFERENCES users (id),
    application_id    UUID         REFERENCES application (id),
    interview_id      UUID         REFERENCES interview (id),
    type              VARCHAR(50)  NOT NULL,
    message           TEXT         NOT NULL,
    status            VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    sent_at           TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_notification_telegram_user_id ON notification (telegram_user_id);
CREATE INDEX idx_notification_application_id ON notification (application_id);
CREATE INDEX idx_notification_interview_id ON notification (interview_id);

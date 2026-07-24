CREATE TABLE notification_delivery (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    vacancy_id         UUID         NOT NULL REFERENCES vacancy (id) ON DELETE CASCADE,
    recipient_chat_id  BIGINT       NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at         TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT now(),
    sent_at            TIMESTAMP,
    failed_at          TIMESTAMP,
    failure_code       VARCHAR(100),
    CONSTRAINT uk_notification_delivery_vacancy_recipient UNIQUE (vacancy_id, recipient_chat_id),
    CONSTRAINT chk_notification_delivery_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT chk_notification_delivery_state CHECK (
        (status = 'PENDING' AND sent_at IS NULL AND failed_at IS NULL) OR
        (status = 'SENT' AND sent_at IS NOT NULL AND failed_at IS NULL) OR
        (status = 'FAILED' AND failed_at IS NOT NULL AND sent_at IS NULL)
    )
);

CREATE INDEX idx_notification_delivery_vacancy_id ON notification_delivery (vacancy_id);

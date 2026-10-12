CREATE TABLE idempotency_keys (
    owner_id      TEXT NOT NULL,
    operation_id  TEXT NOT NULL,
    fingerprint   TEXT NOT NULL,
    response_body TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (owner_id, operation_id)
);

BEGIN;
CREATE TABLE IF NOT EXISTS mail_outbox (
    id uuid PRIMARY KEY,
    kind text NOT NULL CHECK (kind IN ('REGISTRATION_OTP', 'ACCOUNT_EMAIL_OTP', 'PROJECT_INVITATION')),
    aggregate_type text NOT NULL,
    aggregate_id uuid NOT NULL,
    generation integer NOT NULL CHECK (generation > 0),
    recipient text NOT NULL,
    payload_ciphertext bytea,
    nonce bytea,
    key_version integer NOT NULL CHECK (key_version > 0),
    status text NOT NULL CHECK (status IN ('QUEUED', 'SENDING', 'PROVIDER_ACCEPTED', 'RETRY_WAIT', 'UNKNOWN', 'FAILED', 'SUPERSEDED')),
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    lease_id uuid,
    lease_until timestamptz,
    dispatch_started_at timestamptz,
    superseded boolean NOT NULL DEFAULT false,
    provider_message_id text,
    redacted_error_code text,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (aggregate_type, aggregate_id, generation, kind),
    CHECK ((lease_id IS NULL) = (lease_until IS NULL)),
    CHECK ((status = 'SENDING') = (lease_id IS NOT NULL)),
    CHECK ((payload_ciphertext IS NULL) = (nonce IS NULL)),
    CHECK (nonce IS NULL OR octet_length(nonce) = 12),
    CHECK ((status IN ('QUEUED', 'SENDING', 'RETRY_WAIT')) = (payload_ciphertext IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS mail_outbox_ready_idx ON mail_outbox (next_attempt_at, id)
    WHERE status IN ('QUEUED', 'RETRY_WAIT');
CREATE INDEX IF NOT EXISTS mail_outbox_lease_idx ON mail_outbox (lease_until)
    WHERE status = 'SENDING';
CREATE INDEX IF NOT EXISTS mail_outbox_expiry_idx ON mail_outbox (expires_at, id)
    WHERE status IN ('QUEUED', 'RETRY_WAIT', 'SENDING');
COMMIT;

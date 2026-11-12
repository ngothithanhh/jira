BEGIN;

LOCK TABLE users IN SHARE ROW EXCLUSIVE MODE;
DO $$
DECLARE duplicate_ids text;
BEGIN
    SELECT string_agg(user_id::text, ', ' ORDER BY user_id) INTO duplicate_ids
    FROM (SELECT user_id, count(*) OVER (PARTITION BY lower(btrim(email) COLLATE "C")) AS copies FROM users) candidates
    WHERE copies > 1;
    IF duplicate_ids IS NOT NULL THEN
        RAISE EXCEPTION 'Duplicate canonical email. Review users.user_id: %', duplicate_ids;
    END IF;
END $$;

ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified_at timestamptz;
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_canonical_email ON users (lower(btrim(email) COLLATE "C"));

CREATE TABLE IF NOT EXISTS registration_challenges (
    email_normalized varchar(100) PRIMARY KEY,
    registration_id uuid NOT NULL UNIQUE,
    proof_hash bytea,
    generation integer NOT NULL CHECK (generation >= 0),
    username varchar(50),
    full_name varchar(100),
    password_hash varchar(255),
    otp_hmac bytea,
    otp_history bytea NOT NULL DEFAULT ''::bytea CHECK (octet_length(otp_history) <= 992),
    expires_at timestamptz NOT NULL,
    proof_expires_at timestamptz NOT NULL,
    resend_after timestamptz NOT NULL,
    attempt_count integer NOT NULL CHECK (attempt_count BETWEEN 0 AND 5),
    state text NOT NULL CHECK (state IN ('PENDING','LOCKED','CONSUMED','EXPIRED')),
    verified_user_id integer REFERENCES users(user_id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CHECK (expires_at <= proof_expires_at),
    CHECK (state <> 'PENDING' OR (proof_hash IS NOT NULL AND otp_hmac IS NOT NULL AND password_hash IS NOT NULL AND username IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS email_verification_challenges (
    user_id integer PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    email_normalized varchar(100) NOT NULL,
    purpose text NOT NULL DEFAULT 'ACCOUNT_EMAIL' CHECK (purpose = 'ACCOUNT_EMAIL'),
    registration_id uuid NOT NULL UNIQUE,
    proof_hash bytea,
    generation integer NOT NULL CHECK (generation > 0),
    otp_hmac bytea,
    expires_at timestamptz NOT NULL,
    proof_expires_at timestamptz NOT NULL,
    resend_after timestamptz NOT NULL,
    attempt_count integer NOT NULL CHECK (attempt_count BETWEEN 0 AND 5),
    state text NOT NULL CHECK (state IN ('PENDING','LOCKED','CONSUMED','EXPIRED')),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CHECK (expires_at <= proof_expires_at),
    CHECK (state <> 'PENDING' OR (proof_hash IS NOT NULL AND otp_hmac IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS ix_registration_proof_expiry ON registration_challenges(proof_expires_at);
CREATE INDEX IF NOT EXISTS ix_account_email_proof_expiry ON email_verification_challenges(proof_expires_at);

CREATE TABLE IF NOT EXISTS verification_rate_limits (
    subject_hash bytea NOT NULL,
    action text NOT NULL,
    window_start timestamptz NOT NULL,
    attempts integer NOT NULL CHECK (attempts >= 0),
    PRIMARY KEY (subject_hash, action, window_start)
);
CREATE INDEX IF NOT EXISTS ix_verification_rate_expiry ON verification_rate_limits(window_start);

COMMIT;

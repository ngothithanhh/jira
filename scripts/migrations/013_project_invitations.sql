BEGIN;

CREATE TABLE IF NOT EXISTS project_invitations (
    invitation_id uuid PRIMARY KEY,
    project_id integer NOT NULL REFERENCES projects(project_id) ON DELETE RESTRICT,
    inviter_user_id integer REFERENCES users(user_id) ON DELETE SET NULL,
    invited_user_id integer REFERENCES users(user_id) ON DELETE SET NULL,
    email_normalized varchar(100) NOT NULL,
    role_id integer NOT NULL REFERENCES user_roles(role_id) ON DELETE RESTRICT,
    state text NOT NULL CHECK (state IN ('PENDING','ACCEPTED','DECLINED','REVOKED','EXPIRED')),
    token_hash bytea NOT NULL CHECK (octet_length(token_hash) = 32),
    generation integer NOT NULL CHECK (generation BETWEEN 1 AND 4),
    expires_at timestamptz NOT NULL,
    resend_after timestamptz NOT NULL,
    accepted_user_id integer REFERENCES users(user_id) ON DELETE SET NULL,
    decided_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CHECK (email_normalized = lower(btrim(email_normalized) COLLATE "C")),
    CHECK ((state = 'PENDING') = (decided_at IS NULL)),
    CHECK (state = 'ACCEPTED' OR accepted_user_id IS NULL),
    CHECK (expires_at > created_at AND expires_at <= created_at + interval '336 hours')
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_project_invitation_pending
    ON project_invitations(project_id,email_normalized) WHERE state='PENDING';
CREATE INDEX IF NOT EXISTS ix_project_invitation_project
    ON project_invitations(project_id,created_at DESC,invitation_id DESC);
CREATE INDEX IF NOT EXISTS ix_project_invitation_email
    ON project_invitations(email_normalized,created_at DESC,invitation_id DESC);

ALTER TABLE notifications ADD COLUMN IF NOT EXISTS invitation_id uuid;

COMMIT;

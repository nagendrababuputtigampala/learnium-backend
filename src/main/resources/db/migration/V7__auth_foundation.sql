-- V7: M2 auth foundation.
--
-- Three new tables for token-based auth, all centre-owned (centre_id column +
-- composite FKs from day one, per the V5/V6 tenancy-invariant pattern; the
-- single-column FKs are kept alongside for the same reasons as V5):
--   account_token  — single-use invite / password-reset tokens; only the
--                    SHA-256 hash of the raw token is stored.
--   email_outbox   — transactional outbox for mail; written in the caller's
--                    transaction, dispatched asynchronously with retries.
--                    last_error holds exception class names only, never body
--                    content (no client input in operational data).
--   refresh_token  — rotating refresh tokens; only the SHA-256 hash is
--                    stored. Presenting a revoked token is treated as reuse
--                    and revokes every token of that user (service layer).
-- batch_memberships gains the roster-PIN lockout counters used by the
-- student-session flow (5 failed PIN attempts lock the membership PIN until
-- a teacher resets it).

CREATE TABLE account_token (
    account_token_id uuid PRIMARY KEY,
    centre_id        uuid NOT NULL REFERENCES centres (centre_id),
    user_id          uuid NOT NULL REFERENCES user_account (user_id),
    purpose          varchar(20) NOT NULL,
    token_hash       varchar(64) NOT NULL,
    expires_at       timestamp with time zone NOT NULL,
    used_at          timestamp with time zone,
    created_by       uuid,
    created_at       timestamp with time zone NOT NULL,
    updated_by       uuid,
    updated_at       timestamp with time zone NOT NULL,
    CONSTRAINT fk_account_token_centre_user
        FOREIGN KEY (centre_id, user_id) REFERENCES user_account (centre_id, user_id)
);
CREATE INDEX idx_account_token_hash ON account_token (token_hash);
CREATE INDEX idx_account_token_user ON account_token (user_id);

CREATE TABLE email_outbox (
    email_outbox_id uuid PRIMARY KEY,
    centre_id       uuid NOT NULL REFERENCES centres (centre_id),
    recipient       varchar(255) NOT NULL,
    template        varchar(50) NOT NULL,
    model           jsonb NOT NULL,
    status          varchar(20) NOT NULL DEFAULT 'PENDING',
    attempts        int NOT NULL DEFAULT 0,
    last_error      varchar(255),
    created_by      uuid,
    created_at      timestamp with time zone NOT NULL,
    updated_by      uuid,
    updated_at      timestamp with time zone NOT NULL,
    sent_at         timestamp with time zone
);
CREATE INDEX idx_email_outbox_status ON email_outbox (status, created_at);
CREATE INDEX idx_email_outbox_centre ON email_outbox (centre_id);

CREATE TABLE refresh_token (
    refresh_token_id uuid PRIMARY KEY,
    centre_id        uuid NOT NULL REFERENCES centres (centre_id),
    user_id          uuid NOT NULL REFERENCES user_account (user_id),
    token_hash       varchar(64) NOT NULL,
    expires_at       timestamp with time zone NOT NULL,
    revoked_at       timestamp with time zone,
    created_at       timestamp with time zone NOT NULL,
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_centre_user
        FOREIGN KEY (centre_id, user_id) REFERENCES user_account (centre_id, user_id)
);
CREATE INDEX idx_refresh_token_hash ON refresh_token (token_hash);
CREATE INDEX idx_refresh_token_user ON refresh_token (user_id);

ALTER TABLE batch_memberships ADD COLUMN pin_failed_attempts int NOT NULL DEFAULT 0;
ALTER TABLE batch_memberships ADD COLUMN pin_locked boolean NOT NULL DEFAULT false;

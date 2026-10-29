-- V9: per-account security version (M2 fix round 2, Codex rerun P1).
--
-- A password reset revokes every refresh token of the account, but the
-- revocation and a CONCURRENT login were uncoordinated: a login whose
-- credential check read the old password hash just before the reset
-- committed could insert a fresh refresh token AFTER the revoke-all had
-- run, and that token stayed valid for the new password's lifetime.
--
-- The structural fix is a security version. user_account.security_version
-- is incremented in the same transaction as every credential change
-- (password set/reset) and every deactivation. Each refresh_token row is
-- stamped with the account's version at issuance, and rotation refuses a
-- token whose stamp no longer matches the account's current version —
-- whenever the racing token was written, it carries the pre-change
-- version and is dead on first use. Existing rows on both tables are 0,
-- matching each other, so live sessions survive the upgrade.

ALTER TABLE user_account ADD COLUMN security_version bigint NOT NULL DEFAULT 0;
ALTER TABLE refresh_token ADD COLUMN security_version bigint NOT NULL DEFAULT 0;

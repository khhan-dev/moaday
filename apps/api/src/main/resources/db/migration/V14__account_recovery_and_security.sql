ALTER TABLE users
    ADD COLUMN security_version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE users
    ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0;

ALTER TABLE users
    ADD COLUMN locked_until TIMESTAMP WITH TIME ZONE;

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_password_reset_tokens_user_created
    ON password_reset_tokens(user_id, created_at DESC);

ALTER TABLE email_outbox
    ADD COLUMN password_reset_token_id UUID REFERENCES password_reset_tokens(id);

CREATE INDEX idx_email_outbox_password_reset
    ON email_outbox(password_reset_token_id, created_at DESC);

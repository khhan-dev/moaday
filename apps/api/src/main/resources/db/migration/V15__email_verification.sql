CREATE TABLE email_verification_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_email_verification_tokens_user_created
    ON email_verification_tokens(user_id, created_at DESC);

ALTER TABLE email_outbox
    ADD COLUMN email_verification_token_id UUID REFERENCES email_verification_tokens(id);

CREATE INDEX idx_email_outbox_email_verification
    ON email_outbox(email_verification_token_id, created_at DESC);

ALTER TABLE users
    ALTER COLUMN status TYPE VARCHAR(32);
CREATE TABLE email_outbox (
    id UUID PRIMARY KEY,
    space_id UUID REFERENCES spaces(id),
    invitation_id UUID REFERENCES invitations(id),
    category VARCHAR(40) NOT NULL,
    recipient VARCHAR(320) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL,
    max_attempts INTEGER NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    sent_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_email_outbox_due
    ON email_outbox(status, next_attempt_at, created_at);

CREATE INDEX idx_email_outbox_space_created
    ON email_outbox(space_id, created_at DESC);

CREATE INDEX idx_email_outbox_invitation
    ON email_outbox(invitation_id, created_at DESC);

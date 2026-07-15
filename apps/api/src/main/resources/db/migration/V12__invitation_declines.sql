ALTER TABLE invitations
    ADD COLUMN declined_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_invitations_email_pending
    ON invitations(email, expires_at);

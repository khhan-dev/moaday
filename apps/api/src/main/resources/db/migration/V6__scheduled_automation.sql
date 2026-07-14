CREATE TABLE event_reminder_deliveries (
    id UUID PRIMARY KEY,
    reminder_id UUID NOT NULL REFERENCES event_reminders(id) ON DELETE CASCADE,
    occurrence_starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id),
    delivered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (reminder_id, occurrence_starts_at, user_id)
);

CREATE INDEX idx_reminder_deliveries_lookup
    ON event_reminder_deliveries(reminder_id, occurrence_starts_at, user_id);

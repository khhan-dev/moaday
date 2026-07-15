CREATE TABLE event_occurrence_exceptions (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    original_starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    action VARCHAR(20) NOT NULL,
    title VARCHAR(120),
    description VARCHAR(4000),
    location VARCHAR(200),
    all_day BOOLEAN,
    starts_at TIMESTAMP WITH TIME ZONE,
    ends_at TIMESTAMP WITH TIME ZONE,
    timezone VARCHAR(64),
    updated_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_event_occurrence_exception UNIQUE (event_id, original_starts_at),
    CONSTRAINT chk_occurrence_exception_action CHECK (action IN ('OVERRIDE', 'CANCELLED')),
    CONSTRAINT chk_occurrence_override_values CHECK (
        action = 'CANCELLED' OR
        (title IS NOT NULL AND all_day IS NOT NULL AND starts_at IS NOT NULL AND ends_at IS NOT NULL AND timezone IS NOT NULL)
    )
);

CREATE INDEX idx_occurrence_exceptions_event ON event_occurrence_exceptions(event_id, original_starts_at);

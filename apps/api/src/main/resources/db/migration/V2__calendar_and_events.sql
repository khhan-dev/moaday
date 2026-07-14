CREATE TABLE calendars (
    id UUID PRIMARY KEY,
    space_id UUID NOT NULL REFERENCES spaces(id),
    name VARCHAR(60) NOT NULL,
    color VARCHAR(30) NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (space_id, name)
);

CREATE TABLE events (
    id UUID PRIMARY KEY,
    calendar_id UUID NOT NULL REFERENCES calendars(id),
    space_id UUID NOT NULL REFERENCES spaces(id),
    uid VARCHAR(100) NOT NULL UNIQUE,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(4000),
    location VARCHAR(200),
    external_url VARCHAR(1000),
    all_day BOOLEAN NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    recurrence VARCHAR(20) NOT NULL,
    recurrence_until TIMESTAMP WITH TIME ZONE,
    created_by UUID NOT NULL REFERENCES users(id),
    version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE event_attendees (
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    response VARCHAR(20) NOT NULL,
    responded_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (event_id, user_id)
);

CREATE TABLE event_reminders (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    minutes_before INTEGER NOT NULL,
    channel VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (event_id, minutes_before, channel)
);

CREATE INDEX idx_calendars_space ON calendars(space_id);
CREATE INDEX idx_events_space_starts ON events(space_id, starts_at);
CREATE INDEX idx_event_attendees_user ON event_attendees(user_id);
CREATE INDEX idx_event_reminders_event ON event_reminders(event_id);

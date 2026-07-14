CREATE TABLE user_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    app_notifications BOOLEAN NOT NULL,
    email_notifications BOOLEAN NOT NULL,
    event_reminders BOOLEAN NOT NULL,
    post_activity BOOLEAN NOT NULL,
    coupon_activity BOOLEAN NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    space_id UUID REFERENCES spaces(id),
    type VARCHAR(40) NOT NULL,
    title VARCHAR(160) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    link VARCHAR(500),
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_notifications_user_created ON notifications(user_id, created_at);
CREATE INDEX idx_notifications_user_read ON notifications(user_id, read_at);

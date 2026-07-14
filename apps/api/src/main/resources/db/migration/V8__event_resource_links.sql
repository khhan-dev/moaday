CREATE TABLE event_resource_links (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    post_id UUID REFERENCES posts(id) ON DELETE CASCADE,
    attachment_id UUID REFERENCES post_attachments(id) ON DELETE CASCADE,
    coupon_id UUID REFERENCES coupons(id) ON DELETE CASCADE,
    added_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_event_resource_exactly_one CHECK (
        (CASE WHEN post_id IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN attachment_id IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN coupon_id IS NULL THEN 0 ELSE 1 END) = 1
    )
);

CREATE UNIQUE INDEX uq_event_resource_post ON event_resource_links(event_id, post_id);
CREATE UNIQUE INDEX uq_event_resource_attachment ON event_resource_links(event_id, attachment_id);
CREATE UNIQUE INDEX uq_event_resource_coupon ON event_resource_links(event_id, coupon_id);
CREATE INDEX idx_event_resource_event ON event_resource_links(event_id, created_at);

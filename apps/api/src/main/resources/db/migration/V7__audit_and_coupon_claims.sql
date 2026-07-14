CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    space_id UUID NOT NULL REFERENCES spaces(id),
    actor_id UUID REFERENCES users(id),
    action VARCHAR(60) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    resource_id UUID,
    summary VARCHAR(500) NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_audit_logs_space_created ON audit_logs(space_id, created_at DESC);
CREATE INDEX idx_audit_logs_resource_created ON audit_logs(resource_type, resource_id, created_at DESC);

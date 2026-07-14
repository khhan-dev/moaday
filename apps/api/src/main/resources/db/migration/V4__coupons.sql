CREATE TABLE coupons (
    id UUID PRIMARY KEY,
    space_id UUID NOT NULL REFERENCES spaces(id),
    owner_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(120) NOT NULL,
    brand VARCHAR(80) NOT NULL,
    description VARCHAR(2000),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    barcode_value VARCHAR(500) NOT NULL,
    barcode_format VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    claimed_by UUID REFERENCES users(id),
    claimed_at TIMESTAMP WITH TIME ZONE,
    used_by UUID REFERENCES users(id),
    used_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_coupons_space_status_expiry ON coupons(space_id, status, expires_at);
CREATE INDEX idx_coupons_claimed_by ON coupons(claimed_by);

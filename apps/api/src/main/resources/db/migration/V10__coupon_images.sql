CREATE TABLE coupon_images (
    id UUID PRIMARY KEY,
    coupon_id UUID NOT NULL UNIQUE REFERENCES coupons(id) ON DELETE CASCADE,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_key VARCHAR(100) NOT NULL UNIQUE,
    uploaded_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_coupon_images_coupon_id ON coupon_images(coupon_id);

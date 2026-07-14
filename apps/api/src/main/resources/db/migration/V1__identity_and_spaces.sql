CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(40) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE spaces (
    id UUID PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    name VARCHAR(60) NOT NULL,
    owner_user_id UUID NOT NULL REFERENCES users(id),
    timezone VARCHAR(64) NOT NULL,
    color VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE space_members (
    space_id UUID NOT NULL REFERENCES spaces(id),
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    coupon_redeem_allowed BOOLEAN NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (space_id, user_id)
);

CREATE TABLE invitations (
    id UUID PRIMARY KEY,
    space_id UUID NOT NULL REFERENCES spaces(id),
    email VARCHAR(320) NOT NULL,
    role VARCHAR(20) NOT NULL,
    token_hash VARCHAR(100) NOT NULL UNIQUE,
    invited_by UUID NOT NULL REFERENCES users(id),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_space_members_user_status ON space_members(user_id, status);
CREATE INDEX idx_invitations_space_email ON invitations(space_id, email);

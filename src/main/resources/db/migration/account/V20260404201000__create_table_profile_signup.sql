CREATE TABLE profile_signup (
    profile_id BIGINT REFERENCES profile(id) ON DELETE CASCADE,
    signed_up_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(256) NOT NULL,
    PRIMARY KEY (profile_id)
)

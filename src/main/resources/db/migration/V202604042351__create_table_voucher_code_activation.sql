CREATE TABLE voucher_code_activation (
    profile_id BIGINT REFERENCES profile(id) ON DELETE CASCADE,
    voucher_code_id BIGINT REFERENCES voucher_code(id) ON DELETE CASCADE,
    activated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (profile_id, voucher_code_id)
)

CREATE TABLE voucher_codes_activations (
    voucher_code_id BIGINT REFERENCES voucher_codes(id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    activated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (voucher_code_id, user_id)
)
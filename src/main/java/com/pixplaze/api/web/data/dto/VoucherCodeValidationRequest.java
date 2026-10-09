package com.pixplaze.api.web.data.dto;

import com.pixplaze.api.web.data.voucher.VoucherCodeType;

/// Проверка кода по БД; {@code type == null} — код любого типа.
public record VoucherCodeValidationRequest(
        String code,
        VoucherCodeType type
) {
}

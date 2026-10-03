package com.pixplaze.api.web.exception.voucher;

import com.pixplaze.api.web.exception.http.ForbiddenException;

/// Невалидный/просроченный/исчерпанный voucher-код → 403 (наследует {@link ForbiddenException}).
public class VoucherCodeValidationException extends ForbiddenException {
    public VoucherCodeValidationException(String message) {
        super(message);
    }
}

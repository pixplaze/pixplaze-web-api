package com.pixplaze.api.web.exception.voucher;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class VoucherCodeValidationException  extends RuntimeException {
    public VoucherCodeValidationException(String message) {
        super(message);
    }
}

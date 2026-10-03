package com.pixplaze.api.web.exception.voucher;

import com.pixplaze.api.web.exception.http.ForbiddenException;

/// Регистрация закрыта инвайтом / инвайт невалиден → 403 (наследует {@link ForbiddenException}).
public class InvalidInviteCodeException extends ForbiddenException {
    public InvalidInviteCodeException() {
        this("User registration is restricted by invite code.");
    }

    public InvalidInviteCodeException(String message) {
        super(message);
    }
}

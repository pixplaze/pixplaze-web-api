package com.pixplaze.api.web.exception.voucher;

public class InvalidInviteCodeException extends RuntimeException {
    public InvalidInviteCodeException() {
        this("User registration is restricted by invite code.");
    }

    public InvalidInviteCodeException(String message) {
        super(message);
    }
}

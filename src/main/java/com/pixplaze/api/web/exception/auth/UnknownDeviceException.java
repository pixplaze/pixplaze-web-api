package com.pixplaze.api.web.exception.auth;

public class UnknownDeviceException extends RuntimeException {
    public UnknownDeviceException(String message) {
        super(message);
    }
}

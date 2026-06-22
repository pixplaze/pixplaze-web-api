package com.pixplaze.api.web.exception.auth;

public class DeviceAuthorizationException extends RuntimeException {
    public DeviceAuthorizationException() {
    }

    public DeviceAuthorizationException(String message) {
        super(message);
    }
}

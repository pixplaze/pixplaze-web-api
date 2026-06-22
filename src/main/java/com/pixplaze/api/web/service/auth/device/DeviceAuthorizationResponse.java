package com.pixplaze.api.web.service.auth.device;

public record DeviceAuthorizationResponse<T>(String error, T data) {
    public static <T> DeviceAuthorizationResponse<T> error(String error) {
        return new DeviceAuthorizationResponse<>(error, null);
    }

    public static <T> DeviceAuthorizationResponse<T> success(T data) {
        return new DeviceAuthorizationResponse<>(null, data);
    }

    public boolean success() {
        return error == null;
    }
}

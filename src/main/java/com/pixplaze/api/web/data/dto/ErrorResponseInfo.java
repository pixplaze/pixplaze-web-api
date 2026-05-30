package com.pixplaze.api.web.data.dto;

import org.springframework.http.HttpStatus;

public record ErrorResponseInfo(
        int status,
        String timestamp,
        String message,
        String trace,
        String path
) {
    public ErrorResponseInfo withStatus(HttpStatus httpStatus) {
        return new ErrorResponseInfo(httpStatus.value(), timestamp, message, trace, path);
    }
}

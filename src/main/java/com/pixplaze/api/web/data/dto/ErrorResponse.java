package com.pixplaze.api.web.data.dto;

import org.springframework.http.HttpStatus;

public record ErrorResponse(
        int status,
        String timestamp,
        String message,
        String trace,
        String path
) {
    public ErrorResponse withStatus(HttpStatus httpStatus) {
        return new ErrorResponse(httpStatus.value(), timestamp, message, trace, path);
    }
}

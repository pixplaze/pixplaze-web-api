package com.pixplaze.api.web.exception.http;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
public class NotImplementedException extends RuntimeException {
    public NotImplementedException() {
        this("Call is not implemented yet!");
    }

    public NotImplementedException(String message) {
        super(message);
    }
}

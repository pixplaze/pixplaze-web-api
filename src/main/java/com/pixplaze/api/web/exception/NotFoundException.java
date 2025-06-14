package com.pixplaze.api.web.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends NullPointerException {
    public NotFoundException() {
        super();
    }

    public NotFoundException(String s) {
        super(s);
    }
}

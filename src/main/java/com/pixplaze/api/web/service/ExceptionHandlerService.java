package com.pixplaze.api.web.service;

import com.pixplaze.api.web.PixplazeApiApplication;
import com.pixplaze.api.web.data.dto.ErrorResponseInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ExceptionHandlerService {
    private final JsonMapper jsonMapper;
    public ErrorResponseInfo handleException(Throwable throwable, HttpServletRequest httpServletRequest) {
        HttpStatus httpStatus = getHttpStatus(throwable);
        int status = httpStatus.value();
        String timestamp = Instant.now().toString();
        String message = httpStatus.getReasonPhrase();
        String trace = null;
        String path = null;

        if (PixplazeApiApplication.isDevelopment()) {
            message = getDetailedOrDefaultMessage(throwable, message);
            trace = getStackTrace(throwable);
            path = getPathOrDefault(throwable, httpServletRequest == null ? null : httpServletRequest.getRequestURI());
        } else if (httpStatus.is4xxClientError()) {
            message = getDetailedOrDefaultMessage(throwable, message);
        }

        return new ErrorResponseInfo(status, timestamp, message, trace, path);
    }

    public void sendErrorResponseInfo(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Throwable throwable) throws IOException {
        final var errorResponseInfo = handleException(throwable, httpServletRequest);
        try (var writer = httpServletResponse.getWriter()) {
            httpServletResponse.setStatus(errorResponseInfo.status());
            writer.write(stringify(errorResponseInfo));
        }
    }

    public String stringify(ErrorResponseInfo errorResponseInfo) {
        return jsonMapper.writeValueAsString(errorResponseInfo);
    }

    private HttpStatus getHttpStatus(Throwable throwable) {
        if (throwable instanceof ErrorResponse errorResponse) {
            return HttpStatus.resolve(errorResponse.getStatusCode().value());
        } else if (throwable instanceof AccessDeniedException) {
            return HttpStatus.FORBIDDEN;
        } else if (throwable instanceof AuthenticationException) {
            return HttpStatus.UNAUTHORIZED;
        }

        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String getDetailedOrDefaultMessage(Throwable throwable, String defaultMessage) {
        if (throwable == null) {
            return defaultMessage;
        }

        return throwable.getMessage();
    }

    private String getStackTrace(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        return ExceptionUtils.getStackTrace(throwable);
    }

    private String getPathOrDefault(Throwable throwable, String defaultPath) {
        if (throwable instanceof ErrorResponse errorResponse) {
            final var instance = errorResponse.getBody().getInstance();
            if (instance == null) {
                return defaultPath;
            }

            return instance.getPath();
        }

        return defaultPath;
    }

    public static Throwable unwrapException(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        // Список классов-оберток, которые мы хотим пропустить
        if (throwable instanceof jakarta.servlet.ServletException ||
            throwable instanceof java.util.concurrent.ExecutionException ||
            throwable instanceof java.lang.reflect.InvocationTargetException ||
            throwable.getClass().getName().equals("org.springframework.web.util.NestedServletException")) {

            Throwable cause = throwable.getCause();
            if (cause != null) {
                // Рекурсивно идем вглубь, пока не найдем корневую причину
                return unwrapException(cause);
            }
        }
        return throwable;
    }
}

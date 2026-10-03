package com.pixplaze.api.web.service;

import com.pixplaze.api.web.configuration.ApplicationConfiguration;
import com.pixplaze.api.web.data.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ExceptionHandlerService {
    private final JsonMapper jsonMapper;
    private final ApplicationConfiguration applicationConfiguration;

    /// Строит тело ошибки, самостоятельно выводя статус ({@link #getHttpStatus}). Используется на
    /// security-пути (entrypoint/accessDenied — статус интринсивен исключению) и как fallback.
    public ErrorResponse handleException(Throwable throwable, HttpServletRequest httpServletRequest) {
        return handleException(throwable, getHttpStatus(throwable), httpServletRequest);
    }

    /// Строит тело ошибки с ЯВНО заданным статусом. Используется в {@code ApiExceptionHandler},
    /// где статус решает типовой {@code @ExceptionHandler}, а не эвристика по типу исключения.
    public ErrorResponse handleException(Throwable throwable, HttpStatus httpStatus, HttpServletRequest httpServletRequest) {
        int status = httpStatus.value();
        String timestamp = Instant.now().toString();
        String message = httpStatus.getReasonPhrase();
        String trace = null;
        String path = null;

        if (applicationConfiguration.isDevelopment()) {
            message = getDetailedOrDefaultMessage(throwable, message);
            trace = getStackTrace(throwable);
            path = getPathOrDefault(throwable, httpServletRequest == null ? null : httpServletRequest.getRequestURI());
        } else if (httpStatus.is4xxClientError()) {
            message = getDetailedOrDefaultMessage(throwable, message);
        }

        return new ErrorResponse(status, timestamp, message, trace, path);
    }

    /// Пишет ошибку в сырой {@link HttpServletResponse} (security-путь вне MVC), выводя статус сам.
    /// Подходит как {@code AuthenticationEntryPoint}/{@code AccessDeniedHandler}: их исключения
    /// ({@code AuthenticationException}/{@code AccessDeniedException}) корректно мапятся в 401/403.
    public void sendErrorResponseInfo(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Throwable throwable) throws IOException {
        sendErrorResponseInfo(httpServletRequest, httpServletResponse, throwable, getHttpStatus(throwable));
    }

    /// То же, но с ЯВНЫМ статусом — для мест, где тип исключения не несёт статуса (напр. фильтр
    /// токена: любой сбой парсинга/подписи JWT ⇒ 401), чтобы не тащить {@code ResponseStatusException}.
    public void sendErrorResponseInfo(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Throwable throwable, HttpStatus httpStatus) throws IOException {
        final var errorResponseInfo = handleException(throwable, httpStatus, httpServletRequest);
        try (var writer = httpServletResponse.getWriter()) {
            httpServletResponse.setStatus(errorResponseInfo.status());
            writer.write(stringify(errorResponseInfo));
        }
    }

    public String stringify(ErrorResponse errorResponse) {
        return jsonMapper.writeValueAsString(errorResponse);
    }

    private HttpStatus getHttpStatus(Throwable throwable) {
        if (throwable instanceof org.springframework.web.ErrorResponse errorResponse) {
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
        if (throwable instanceof org.springframework.web.ErrorResponse errorResponse) {
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

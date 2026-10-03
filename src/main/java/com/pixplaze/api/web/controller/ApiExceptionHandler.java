package com.pixplaze.api.web.controller;

import com.pixplaze.api.web.data.dto.ErrorResponse;
import com.pixplaze.api.web.exception.auth.DeviceAuthorizationError;
import com.pixplaze.api.web.exception.auth.DeviceAuthorizationException;
import com.pixplaze.api.web.exception.http.BadRequestException;
import com.pixplaze.api.web.exception.http.ConflictException;
import com.pixplaze.api.web.exception.http.ForbiddenException;
import com.pixplaze.api.web.exception.http.NotFoundException;
import com.pixplaze.api.web.exception.http.NotImplementedException;
import com.pixplaze.api.web.exception.http.ServiceUnavailableException;
import com.pixplaze.api.web.exception.http.UnauthorizedException;
import com.pixplaze.api.web.service.ExceptionHandlerService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.MissingClaimException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;

/**
 * Единая точка перевода исключений в HTTP-ответ для MVC-пути (модель C: диспетчеризация по типу,
 * статус живёт здесь, доменные исключения — чистые POJO). Наследует {@link ResponseEntityExceptionHandler},
 * чтобы фреймворковые ошибки (валидация {@code @Min/@Max} → {@code HandlerMethodValidationException},
 * нечитаемое тело, неверный метод и т.п.) тоже сводились к общему {@link ErrorResponse}.
 *
 * <p>Формат тела единый ({@link ErrorResponse}) во всех кейсах, КРОМЕ OAuth device-flow, где ответ
 * диктуется стандартом (RFC 8628/6749) — {@code {"error": ...}}. Security-путь форматируется тем же
 * {@link ExceptionHandlerService} через хуки в {@code SecurityConfiguration}/{@code JwtAuthenticationFilter}.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private final ExceptionHandlerService exceptionHandlerService;

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> badRequest(BadRequestException e, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, e, request);
    }

    /// Ловит и доменный {@code InvalidRefreshTokenException} (наследник).
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> unauthorized(UnauthorizedException e, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, e, request);
    }

    /// Ловит доменные {@code VoucherCodeValidationException}/{@code InvalidInviteCodeException} (наследники).
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> forbidden(ForbiddenException e, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, e, request);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NotFoundException e, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, e, request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> conflict(ConflictException e, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, e, request);
    }

    @ExceptionHandler(NotImplementedException.class)
    public ResponseEntity<ErrorResponse> notImplemented(NotImplementedException e, HttpServletRequest request) {
        return respond(HttpStatus.NOT_IMPLEMENTED, e, request);
    }

    /// Ловит доменный {@code MinecraftServerIsUnavailableException} (наследник).
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> serviceUnavailable(ServiceUnavailableException e, HttpServletRequest request) {
        return respond(HttpStatus.SERVICE_UNAVAILABLE, e, request);
    }

    /// Сбой парсинга/подписи/срока JWT или неверные креды при обмене токенов → 401.
    @ExceptionHandler({
            SignatureException.class,
            DecodingException.class,
            MalformedJwtException.class,
            UnsupportedJwtException.class,
            ExpiredJwtException.class,
            BadCredentialsException.class
    })
    public ResponseEntity<ErrorResponse> invalidToken(Exception e, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, e, request);
    }

    /// Токен валиден, но claim'ы не подходят под операцию → 403.
    @ExceptionHandler({IncorrectClaimException.class, MissingClaimException.class})
    public ResponseEntity<ErrorResponse> invalidClaims(Exception e, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, e, request);
    }

    /// OAuth token/authorize endpoint: формат тела диктуется стандартом (не {@link ErrorResponse}).
    @ExceptionHandler(DeviceAuthorizationException.class)
    public ResponseEntity<Map<String, String>> deviceAuthorization(DeviceAuthorizationException e) {
        final var status = e.getError() == DeviceAuthorizationError.SERVER_ERROR
                ? HttpStatus.INTERNAL_SERVER_ERROR
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Map.of("error", e.getError().getCode()));
    }

    // НАМЕРЕННО НЕТ @ExceptionHandler(Exception.class): слепой catch-all перехватывал бы
    // AccessDeniedException/AuthenticationException от method-security (@PreAuthorize) РАНЬШЕ, чем
    // ExceptionTranslationFilter, и отдавал бы 500 вместо 401/403 (плюс терял бы различие аноним→401).
    // Security-исключения пропускаем до фильтра; всё прочее неопознанное всплывает в контейнерный
    // /error (FormattedErrorController) → тот же ErrorResponse со статусом 500.

    /// Точка, через которую {@link ResponseEntityExceptionHandler} отдаёт ВСЕ фреймворковые ошибки —
    /// переупаковываем дефолтный {@code ProblemDetail} в наш {@link ErrorResponse}, сохраняя статус.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request
    ) {
        final var errorResponse = exceptionHandlerService.handleException(
                ex, HttpStatus.valueOf(statusCode.value()), servletRequest(request));
        return new ResponseEntity<>(errorResponse, headers, statusCode);
    }

    private ResponseEntity<ErrorResponse> respond(HttpStatus status, Exception e, HttpServletRequest request) {
        return ResponseEntity.status(status).body(exceptionHandlerService.handleException(e, status, request));
    }

    private static HttpServletRequest servletRequest(WebRequest request) {
        return request instanceof ServletWebRequest servletWebRequest ? servletWebRequest.getRequest() : null;
    }
}

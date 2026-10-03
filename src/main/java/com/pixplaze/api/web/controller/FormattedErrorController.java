package com.pixplaze.api.web.controller;

import com.pixplaze.api.web.data.dto.ErrorResponse;
import com.pixplaze.api.web.service.ExceptionHandlerService;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/// Контейнерный `/error` (ErrorController). Обработку исключений MVC-пути ведёт {@code ApiExceptionHandler};
/// здесь НЕ должно быть {@code @RestControllerAdvice}/{@code @ExceptionHandler} — иначе снова перехватит всё
/// и затенит типовые хендлеры/статусы.
@RestController
public class FormattedErrorController implements ErrorController {

    private final ErrorAttributes errorAttributes;
    private final ExceptionHandlerService exceptionHandlerService;

    public FormattedErrorController(ErrorAttributes errorAttributes, ExceptionHandlerService exceptionHandlerService) {
        this.errorAttributes = errorAttributes;
        this.exceptionHandlerService = exceptionHandlerService;
    }

    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> handleException(WebRequest webRequest, HttpServletRequest httpServletRequest) {
        final var exception = ExceptionHandlerService.unwrapException(errorAttributes.getError(webRequest));
        // Статус берём из контейнерного атрибута дисптача (jakarta.servlet.error.status_code) — он
        // авторитетен для sendError/404-без-хендлера, где исключения может не быть вовсе. Только если
        // атрибут отсутствует/нераспознан (напр. прямой GET /error) — выводим по исключению.
        final var status = servletErrorStatus(httpServletRequest);
        final var errorResponseInfo = status != null
                ? exceptionHandlerService.handleException(exception, status, httpServletRequest)
                : exceptionHandlerService.handleException(exception, httpServletRequest);

        return ResponseEntity.status(errorResponseInfo.status()).body(errorResponseInfo);
    }

    private static HttpStatus servletErrorStatus(HttpServletRequest request) {
        return request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code
                ? HttpStatus.resolve(code)
                : null;
    }
}

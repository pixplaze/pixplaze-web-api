package com.pixplaze.api.web.controller;

import com.pixplaze.api.web.data.dto.ErrorResponse;
import com.pixplaze.api.web.service.ExceptionHandlerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestController
@RestControllerAdvice
public class FormattedErrorController implements ErrorController {

    private final ErrorAttributes errorAttributes;
    private final ExceptionHandlerService exceptionHandlerService;

    public FormattedErrorController(ErrorAttributes errorAttributes, ExceptionHandlerService exceptionHandlerService) {
        this.errorAttributes = errorAttributes;
        this.exceptionHandlerService = exceptionHandlerService;
    }

    // TODO: Убрал этот RequestMapping, с ним приходится обрабатывать вообще все ошибки, если не понадобится - удалить
//    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> handleException(WebRequest webRequest, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        final var exception = ExceptionHandlerService.unwrapException(errorAttributes.getError(webRequest));
        final var errorResponseInfo = exceptionHandlerService.handleException(
                exception,
                httpServletRequest
        );

        return ResponseEntity.status(errorResponseInfo.status()).body(errorResponseInfo);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception, HttpServletRequest httpServletRequest) {
        final var errorResponseInfo = exceptionHandlerService.handleException(exception, httpServletRequest);
        return ResponseEntity.status(errorResponseInfo.status()).body(errorResponseInfo);
    }
}

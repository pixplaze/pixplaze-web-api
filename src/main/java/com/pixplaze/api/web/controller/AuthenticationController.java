package com.pixplaze.api.web.controller;

import com.pixplaze.api.web.data.dto.ErrorResponseInfo;
import com.pixplaze.api.web.data.dto.JwtAuthenticationResponseInfo;
import com.pixplaze.api.web.data.dto.SignInRequestInfo;
import com.pixplaze.api.web.data.dto.SignUpRequestInfo;
import com.pixplaze.api.web.exception.InvalidInviteCodeException;
import com.pixplaze.api.web.exception.http.NotImplementedException;
import com.pixplaze.api.web.exception.voucher.VoucherCodeValidationException;
import com.pixplaze.api.web.service.AuthenticationService;
import com.pixplaze.api.web.service.ExceptionHandlerService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.MissingClaimException;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.SignatureException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Аутентификация")
@RequestMapping("/auth")
public class AuthenticationController {
    private static final String REFRESH_URL = "/auth/refresh";

    private final ExceptionHandlerService exceptionHandlerService;
    private final AuthenticationService authenticationService;

    @Operation(summary = "Регистрация пользователя")
    @PostMapping("/sign-up")
    public ResponseEntity<JwtAuthenticationResponseInfo> signUp(@RequestBody @Valid SignUpRequestInfo requestInfo) {
        try {
            final var responseInfo = authenticationService.signUp(requestInfo);
            final var responseCookie = authenticationService.createRefreshTokenCookie(responseInfo.refreshToken(), REFRESH_URL);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                    .body(responseInfo.response());
        } catch (InvalidInviteCodeException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    @Operation(summary = "Авторизация пользователя")
    @PostMapping("/sign-in")
    public ResponseEntity<JwtAuthenticationResponseInfo> signIn(@RequestBody @Valid SignInRequestInfo requestInfo) {
        final var responseInfo = authenticationService.signIn(requestInfo);
        final var responseCookie = authenticationService.createRefreshTokenCookie(responseInfo.refreshToken(), REFRESH_URL);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                .body(responseInfo.response());
    }

    @Operation(summary = "Выйти из профиля")
    @PostMapping("/sign-out")
    public JwtAuthenticationResponseInfo signOut() {
        throw new NotImplementedException();
    }

    @PostMapping("/refresh")
    public ResponseEntity<JwtAuthenticationResponseInfo> refresh(
            @CookieValue(name = "refreshToken") String refreshToken
    ) {
        final var jwtAuthenticationResponseInfo = authenticationService.refresh(refreshToken);
        final var responseCookie = authenticationService.createRefreshTokenCookie(jwtAuthenticationResponseInfo.refreshToken(), REFRESH_URL);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                .body(jwtAuthenticationResponseInfo.response());
    }

    @ExceptionHandler({SignatureException.class, DecodingException.class, MalformedJwtException.class, ExpiredJwtException.class, BadCredentialsException.class})
    public ResponseEntity<ErrorResponseInfo> handleAuthenticationException(Exception exception, HttpServletRequest httpServletRequest) {
        final var errorResponseInfo = exceptionHandlerService.handleException(exception, httpServletRequest).withStatus(HttpStatus.UNAUTHORIZED);
        return ResponseEntity.status(errorResponseInfo.status()).body(errorResponseInfo);
    }

    @ExceptionHandler({VoucherCodeValidationException.class, IncorrectClaimException.class, MissingClaimException.class})
    public ResponseEntity<ErrorResponseInfo> handleTokenValidationException(Exception exception, HttpServletRequest httpServletRequest) {
        final var errorResponseInfo = exceptionHandlerService.handleException(exception, httpServletRequest).withStatus(HttpStatus.FORBIDDEN);
        return ResponseEntity.status(errorResponseInfo.status()).body(errorResponseInfo);
    }
}


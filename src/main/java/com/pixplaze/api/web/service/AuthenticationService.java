package com.pixplaze.api.web.service;

import com.pixplaze.api.web.data.VoucherCode;
import com.pixplaze.api.web.data.dto.JwtAuthenticationResponseInfo;
import com.pixplaze.api.web.data.dto.SignInRequestInfo;
import com.pixplaze.api.web.data.dto.SignUpRequestInfo;
import com.pixplaze.api.web.data.user.Role;
import com.pixplaze.api.web.data.user.User;
import com.pixplaze.api.web.service.auth.AccessTokenService;
import com.pixplaze.api.web.service.auth.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final UserService userService;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final VoucherCodeService voucherCodeService;

    /**
     * Регистрация пользователя
     *
     * @param request данные пользователя
     * @return токен
     */
    @Transactional
    public JwtAuthenticationResponseInfo signUp(SignUpRequestInfo request) {

        VoucherCode voucherCode = voucherCodeService.load(request.inviteCode(), VoucherCode.Type.INVITE);

        var user = User.builder()
                .name(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.ROLE_USER)
                .build();

        try {
            userService.create(user);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage(), e);
        }

        voucherCodeService.activate(voucherCode, user);

        var accessToken = accessTokenService.generate(user);
        var refreshToken = refreshTokenService.generate(user);
        return new JwtAuthenticationResponseInfo(accessToken, refreshToken);
    }

    /**
     * Аутентификация пользователя
     *
     * @param request данные пользователя
     * @return токен
     */
    public JwtAuthenticationResponseInfo signIn(SignInRequestInfo request) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        var user = userService.getUserByUsername(request.username());
        var accessToken = accessTokenService.generate(user);
        var refreshToken = refreshTokenService.generate(user);
        return new JwtAuthenticationResponseInfo(accessToken, refreshToken);
    }

    public JwtAuthenticationResponseInfo refresh(String token) {
        final var user = refreshTokenService.extractUser(token);
        final var accessToken = accessTokenService.generate(user);
        final var refreshToken = refreshTokenService.generate(user);
        return new JwtAuthenticationResponseInfo(accessToken, refreshToken);
    }

    public ResponseCookie createRefreshTokenCookie(String refreshToken, String path) {
        return ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true) // Защита от XSS (JS не сможет прочитать куку)
                .secure(true)   // Только через HTTPS
                .path(path)     // Кука будет отправляться только на эндпоинт обновления
                .maxAge(refreshTokenService.getExpirationTerm())
                .build();
    }

    public boolean isInviteCodeValid(String inviteCode) {
        return inviteCode != null && !inviteCode.isBlank() && inviteCode.equals("PIPIDASTR");
    }
}

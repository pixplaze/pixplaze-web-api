package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.ext.data.auth.AuthorizationTokenInfo;
import com.pixplaze.api.web.data.VoucherCode;
import com.pixplaze.api.web.data.db.tables.pojos.Profile;
import com.pixplaze.api.web.data.dto.SignInRequestInfo;
import com.pixplaze.api.web.data.dto.SignUpRequestInfo;
import com.pixplaze.api.web.service.ProfileService;
import com.pixplaze.api.web.service.VoucherCodeService;
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
public class AuthorizationService {
    private final ProfileService profileService;
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
    public AuthorizationTokenInfo signUp(SignUpRequestInfo request) {

        VoucherCode voucherCode = voucherCodeService.load(request.inviteCode(), VoucherCode.Type.INVITE);
        var profile = new Profile()
                .setName(request.username())
                .setEmail(request.email())
                .setPassword(passwordEncoder.encode(request.password()));
        var clientPrincipial = profileService.toClientPrincipial(profile);

        try {
            var id = profileService.create(profile).getId();
            clientPrincipial.setId(id);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage(), e);
        }

        voucherCodeService.activate(voucherCode, profile);

        var accessToken = accessTokenService.generate(clientPrincipial);
        var refreshToken = refreshTokenService.generate(clientPrincipial);
        return new AuthorizationTokenInfo(accessToken, refreshToken);
    }

    /// Аутентификация пользователя
    ///
    /// @param request данные пользователя
    /// @return токен
    public AuthorizationTokenInfo signIn(SignInRequestInfo request) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        var profile = profileService.getUserByUsername(request.username());
        var clientPrincipial = profileService.toClientPrincipial(profile);
        var accessToken = accessTokenService.generate(clientPrincipial);
        var refreshToken = refreshTokenService.generate(clientPrincipial);
        return new AuthorizationTokenInfo(accessToken, refreshToken);
    }

    public AuthorizationTokenInfo refresh(String token) {
        final var clientPrincipial = refreshTokenService.readClaims(token);
        final var accessToken = accessTokenService.generate(clientPrincipial);
        final var refreshToken = refreshTokenService.generate(clientPrincipial);
        return new AuthorizationTokenInfo(accessToken, refreshToken);
    }

    public ResponseCookie createRefreshTokenCookie(String refreshToken, String path) {
        return ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true) // Защита от XSS (JS не сможет прочитать куку)
                .secure(true)   // Только через HTTPS
                .path(path)     // Кука будет отправляться только на эндпоинт обновления
                .maxAge(refreshTokenService.getExpirationTerm())
                .build();
    }
}

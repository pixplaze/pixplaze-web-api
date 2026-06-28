package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.web.data.user.ClientPrincipal;
import io.jsonwebtoken.Locator;

import java.security.Key;
import java.time.Duration;

/**
 * Добавляет к {@link AbstractClientTokenService} подпись/верификацию ES256, делегируя
 * ключ общему {@link AccessTokenKeys} (парсинг ключа и регистрация JWKS — один раз на него,
 * не на каждый сервис-подкласс).
 */
public abstract class AbstractEsClientTokenService<E extends ClientPrincipal> extends AbstractClientTokenService<E> {

    private final AccessTokenKeys keys;

    protected AbstractEsClientTokenService(AccessTokenKeys keys, Duration expirationTerm) {
        super(expirationTerm);
        this.keys = keys;
    }

    @Override
    protected Key signingKey() {
        return keys.signingKey();
    }

    @Override
    protected String signingKid() {
        return keys.kid();
    }

    @Override
    protected Locator<Key> verificationKeyLocator() {
        return keys.verificationKeyLocator();
    }

    /// Доступ к общему ключу для подклассов, которым нужна выдача публичного ключа (только серверный).
    protected AccessTokenKeys keys() {
        return keys;
    }
}

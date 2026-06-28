package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.web.data.user.ClientPrincipal;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * База token-сервисов, выпускающих access-токен на конкретный тип {@link ClientPrincipal}.
 * Общая часть {@code buildClaims} (роли/источник/права через {@link AccessTokenClaims}) +
 * абстрактная запись identity конкретного принципала. Чтение токена обратно в принципал —
 * не здесь: это static {@code toPrincipal(...)} у каждого подкласса, маршрутизируемые
 * {@code ClientPrincipalReader} по ролям.
 *
 * <p>Подпись/верификацию (ES256) добавляет {@link AbstractEsClientTokenService} через общий
 * {@link AccessTokenKeys}.
 *
 * @param <E> конкретный тип принципала-идентичности
 */
public abstract class AbstractClientTokenService<E extends ClientPrincipal> extends AbstractTokenService<E> {

    protected AbstractClientTokenService(Duration expirationTerm) {
        super(expirationTerm);
    }

    @Override
    protected String subjectOf(E identity) {
        return identity.getName();
    }

    /// {@code aud} токена ≡ {@link com.pixplaze.api.ext.data.Authority#targets()} принципала.
    /// Единый источник аудитории: она проставляется в момент выдачи (host игрока, gateway+host
    /// сервера, gateway+хосты-операторские у профиля) и переживает ротацию (auth_targets хранится
    /// в refresh-токене и реплеится). Поэтому отдельных override'ов аудитории у подклассов нет.
    @Override
    protected Collection<String> audience(E identity) {
        return identity.getAuthority().targets();
    }

    @Override
    public Map<String, Object> buildClaims(E identity) {
        final var claims = new HashMap<String, Object>();
        AccessTokenClaims.writeAuthority(claims, identity.getAuthority());
        writeIdentityClaims(identity, claims);
        return claims;
    }

    /// Записывает identity конкретного принципала (profile→pid/email; player→mc{uuid}+pid; server→mc{sid,host}).
    protected abstract void writeIdentityClaims(E identity, Map<String, Object> claims);
}

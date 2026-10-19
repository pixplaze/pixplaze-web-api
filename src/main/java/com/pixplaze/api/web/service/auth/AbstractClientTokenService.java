package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.ext.data.auth.AuthorityClaims;
import com.pixplaze.api.web.data.user.ClientPrincipal;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * База token-сервисов, выпускающих access-токен на конкретный тип {@link ClientPrincipal}.
 * Общая часть {@code buildClaims} (роли и источник через {@link AuthorityClaims}) +
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

    /// {@code aud} токена ≡ {@link com.pixplaze.api.ext.data.auth.Authority#targets()} принципала.
    /// Аудиторию проставляет тот, кто собирает принципал: стратегия device-flow при выдаче и
    /// {@code AuthorizationService.refresh} при ротации (выводит её из БД). Поэтому отдельных
    /// override'ов аудитории у подклассов нет.
    @Override
    protected Collection<String> audience(E identity) {
        return identity.getAuthority().targets();
    }

    @Override
    public Map<String, Object> buildClaims(E identity) {
        final var claims = new HashMap<String, Object>();
        AuthorityClaims.write(claims, identity.getAuthority());
        writeIdentityClaims(identity, claims);
        return claims;
    }

    /// Записывает identity конкретного принципала (profile→pid/email; player→mc{uuid}+pid; server→mc{sid,host}).
    protected abstract void writeIdentityClaims(E identity, Map<String, Object> claims);
}

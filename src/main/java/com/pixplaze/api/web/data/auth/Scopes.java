package com.pixplaze.api.web.data.auth;

import com.pixplaze.api.ext.data.auth.Authority;
import com.pixplaze.api.ext.data.oauth.OAuthError;
import com.pixplaze.api.web.exception.auth.DeviceAuthorizationException;

import java.util.Locale;
import java.util.Set;

/**
 * Преобразование строки {@code scope} запроса device-flow в {@link Authority}.
 *
 * <p>Вынесено из сервиса, потому что нужно с обеих сторон: при создании сессии (разобрать
 * пришедший scope) и при каждом чтении из хранилища (восстановить привилегию по сохранённой
 * строке). В сессии хранится именно строка — компактная, стабильная и не зависящая от того,
 * как устроен класс {@link Authority} внутри.
 */
public final class Scopes {

    /// Сочетания, которые обслуживают стратегии device-flow. Остальное — {@code invalid_scope}
    /// (RFC 6749 §5.2): роль задаёт запрашивающее устройство, и без белого списка оно могло бы
    /// запросить, например, {@code AAD:ADMIN}, а любой одобривший пользователь получил бы эту роль.
    private static final Set<String> SUPPORTED = Set.of(
            "AAD:USER",
            "MAD:MINECRAFT_SERVER",
            "MAD:MINECRAFT_PLAYER",
            "MAD:MINECRAFT_OPERATOR"
    );

    private Scopes() {
    }

    /**
     * Формат — {@code "<source>:<role>"}, например {@code "MAD:MINECRAFT_SERVER"}. Пустой или
     * отсутствующий scope означает обычный вход пользователя приложения.
     *
     * <p>Привилегия всегда «неавторизованная»: это шаблон запрошенных прав, по которому стратегия
     * потом собирает то, что реально выдаётся субъекту.
     *
     * @throws DeviceAuthorizationException {@code invalid_scope}, если scope не разбирается или
     *                                      сочетание не поддерживается (в т.ч. несколько значений через пробел)
     */
    public static Authority toAuthority(String scope) {
        if (scope == null || scope.trim().isBlank()) {
            return Authority.as(Authority.Role.USER)
                    .from(Authority.Source.APPLICATION_AUTHORIZED_DEVICE)
                    .unauthorized();
        }

        final var parts = scope.trim().split(":", -1);
        if (parts.length != 2) {
            throw invalidScope();
        }

        final var source = Authority.Source.of(parts[0]);
        final Authority.Role role;
        try {
            role = Authority.Role.of(parts[1].toUpperCase(Locale.ROOT));
        } catch (IllegalStateException e) {
            throw invalidScope();
        }

        if (!SUPPORTED.contains(source.code() + ":" + role.name())) {
            throw invalidScope();
        }

        return Authority.as(role).from(source).unauthorized();
    }

    private static DeviceAuthorizationException invalidScope() {
        return new DeviceAuthorizationException(OAuthError.INVALID_SCOPE);
    }
}

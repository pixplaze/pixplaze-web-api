package com.pixplaze.api.web.data.auth;

import com.pixplaze.api.ext.data.Authority;

/**
 * Преобразование строки {@code scope} запроса device-flow в {@link Authority}.
 *
 * <p>Вынесено из сервиса, потому что нужно с обеих сторон: при создании сессии (разобрать
 * пришедший scope) и при каждом чтении из хранилища (восстановить привилегию по сохранённой
 * строке). В сессии хранится именно строка — компактная, стабильная и не зависящая от того,
 * как устроен класс {@link Authority} внутри.
 */
public final class Scopes {

    private Scopes() {
    }

    /**
     * Формат — {@code "<source>:<role>"}, например {@code "MAD:MINECRAFT_SERVER"}. Пустой или
     * отсутствующий scope означает обычный вход пользователя приложения.
     *
     * <p>Привилегия всегда «неавторизованная»: это шаблон запрошенных прав, по которому стратегия
     * потом собирает то, что реально выдаётся субъекту (анти-эскалация).
     */
    public static Authority toAuthority(String scope) {
        if (scope == null || scope.trim().isBlank()) {
            return Authority.as(Authority.Role.USER)
                    .from(Authority.Source.APPLICATION_AUTHORIZED_DEVICE)
                    .unauthorized();
        }

        final var split = scope.split(":");
        final var source = Authority.Source.of(split[0]);
        final var role = Authority.Role.of(split[1]);

        return Authority.as(role).from(source).unauthorized();
    }
}

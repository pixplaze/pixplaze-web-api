package com.pixplaze.api.web.data.server;

/**
 * Стадия регистрации сервера ({@code minecraft_server.status}). Заявка — это сервер в статусе {@link #PENDING}:
 * отдельной таблицы заявок нет, коды регистрации лежат в {@code minecraft_server_bid}. В листинге, поиске и
 * прочих чтениях участвуют только {@link #ACTIVE}; бан — отдельно ({@code banned_at}).
 */
public enum MinecraftServerRegistrationStatus {
    /// Заявка подана, сервер ещё не подтвердил код (пингом MOTD или плагином)
    PENDING,
    /// Зарегистрирован
    ACTIVE,
    /// Заявку отклонил администратор
    REJECTED,
    /// Код не подтвердили за срок жизни заявки
    EXPIRED
}

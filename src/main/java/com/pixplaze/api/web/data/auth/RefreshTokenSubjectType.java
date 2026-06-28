package com.pixplaze.api.web.data.auth;

/**
 * Тип субъекта refresh-токена. Определяет, какая owner-колонка значима в {@code refresh_token}
 * и какой принципал восстанавливать при ротации.
 */
public enum RefreshTokenSubjectType {
    PROFILE,
    MINECRAFT_PLAYER,
    MINECRAFT_OPERATOR,
    MINECRAFT_SERVER
}

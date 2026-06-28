package com.pixplaze.api.web.repository;

import java.security.PublicKey;
import java.util.Map;
import java.util.Optional;

/**
 * Хранилище публичных ключей проверки подписи (kid → ключ): источник для
 * верификации токенов и для выдачи JWKS.
 */
public interface JwksRepository {

    /** Регистрирует/обновляет публичный ключ под идентификатором {@code kid}. */
    void put(String kid, PublicKey key);

    /** Публичный ключ по {@code kid} для проверки подписи. */
    Optional<PublicKey> findByKid(String kid);

    /** Все публичные ключи в формате JWKS ({@code {"keys":[...]}}) для эндпоинта раздачи. */
    Map<String, Object> asJwkSet();
}

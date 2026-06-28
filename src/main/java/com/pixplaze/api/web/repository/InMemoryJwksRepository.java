package com.pixplaze.api.web.repository;

import io.jsonwebtoken.security.Jwks;
import org.springframework.stereotype.Component;

import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link JwksRepository} на {@link ConcurrentHashMap}. Достаточно для
 * одного инстанса (или нескольких с одним ключом). Для общего набора ключей
 * между инстансами/ротации без редеплоя реализацию заменит Redis.
 */
@Component
public class InMemoryJwksRepository implements JwksRepository {

    private final Map<String, PublicKey> keysByKid = new ConcurrentHashMap<>();

    @Override
    public void put(String kid, PublicKey key) {
        keysByKid.put(kid, key);
    }

    @Override
    public Optional<PublicKey> findByKid(String kid) {
        return kid == null ? Optional.empty() : Optional.ofNullable(keysByKid.get(kid));
    }

    @Override
    public Map<String, Object> asJwkSet() {
        final var keys = keysByKid.entrySet().stream()
                .<Map<String, Object>>map(entry -> new LinkedHashMap<>(
                        Jwks.builder()
                                .key((ECPublicKey) entry.getValue())
                                .id(entry.getKey())
                                .build()))
                .toList();
        return Map.of("keys", keys);
    }
}

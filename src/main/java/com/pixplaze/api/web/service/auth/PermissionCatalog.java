package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.ext.data.auth.Permissions;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * Каталог прав для фронта ({@code GET /auth/permissions}): одна общая карта «имя константы →
 * код права» из {@link Permissions} ext-api. Версия — хеш содержимого, считается один раз при
 * старте и меняется ровно тогда, когда меняется список прав; отдаётся как {@code ETag}.
 */
@Service
public class PermissionCatalog {

    private final Map<String, String> permissions;
    private final String version;

    public PermissionCatalog() {
        this.permissions = Collections.unmodifiableMap(readConstants());
        this.version = hash(permissions);
    }

    public Map<String, String> permissions() {
        return permissions;
    }

    /// Хеш содержимого каталога (SHA-256, hex), значение {@code ETag} без кавычек.
    public String version() {
        return version;
    }

    private static Map<String, String> readConstants() {
        final var constants = new TreeMap<String, String>();
        Arrays.stream(Permissions.class.getFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == String.class)
                .forEach(field -> constants.put(field.getName(), valueOf(field)));
        return constants;
    }

    private static String valueOf(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read permission constant " + field.getName(), e);
        }
    }

    private static String hash(Map<String, String> permissions) {
        try {
            final var digest = MessageDigest.getInstance("SHA-256");
            permissions.forEach((name, code) -> digest.update((name + '=' + code + '\n').getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}

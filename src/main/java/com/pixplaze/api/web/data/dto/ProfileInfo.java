package com.pixplaze.api.web.data.dto;

import java.time.OffsetDateTime;

/// Профиль для API: строка {@code profile} без хэша пароля.
public record ProfileInfo(
        Long id,
        String name,
        String email,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt
) {
}

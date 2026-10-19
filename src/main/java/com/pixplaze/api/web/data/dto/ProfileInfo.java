package com.pixplaze.api.web.data.dto;

import java.time.OffsetDateTime;

/// Профиль для API: строка {@code profile} без хэша пароля.
///
/// @param isOnlineHidden настройка «скрывать мой онлайн»
public record ProfileInfo(
        Long id,
        String name,
        String email,
        Boolean isOnlineHidden,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt
) {
}

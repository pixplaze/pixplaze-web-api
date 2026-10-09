package com.pixplaze.api.web.service.server.event;

/// Изменились оценки сервера; агрегат пересчитывается после коммита.
public record MinecraftServerRatingChanged(long minecraftServerId) {
}

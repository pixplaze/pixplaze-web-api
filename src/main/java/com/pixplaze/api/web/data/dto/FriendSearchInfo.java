package com.pixplaze.api.web.data.dto;

/**
 * Профиль из поиска друзей.
 *
 * @param skinHeadBase64 голова скина одного из игроков профиля — того, чей ник совпал с запросом, иначе
 *                       первого по нику; Base64 PNG без {@code data:}; {@code null} — ни у одного игрока нет
 * @param status         отношение к искавшему: {@code MUTUAL}, {@code OUTGOING}, {@code INCOMING} или
 *                       {@code NONE} — не связаны
 */
public record FriendSearchInfo(Long profileId, String name, String skinHeadBase64, FriendInfo.Status status) {
}

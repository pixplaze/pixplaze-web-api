package com.pixplaze.api.web.data.dto;

/**
 * Друг профиля.
 *
 * @param status {@code MUTUAL} — дружба (обе строки), {@code OUTGOING} — профиль добавил, ответа нет,
 *               {@code INCOMING} — профиль добавили, он не ответил
 */
public record FriendInfo(Long profileId, String name, Status status) {

    public enum Status {
        MUTUAL,
        OUTGOING,
        INCOMING
    }
}

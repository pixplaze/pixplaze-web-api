package com.pixplaze.api.web.data.dto;

/**
 * Друг профиля.
 *
 * @param status {@code MUTUAL} — дружба (обе строки), {@code OUTGOING} — профиль добавил, ответа нет,
 *               {@code INCOMING} — профиль добавили, он не ответил; {@code NONE} — не связаны (только
 *               в поиске, {@link FriendSearchInfo})
 */
public record FriendInfo(Long profileId, String name, Status status) {

    public enum Status {
        MUTUAL,
        OUTGOING,
        INCOMING,
        NONE
    }
}

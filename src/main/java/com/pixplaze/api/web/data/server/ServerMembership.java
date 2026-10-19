package com.pixplaze.api.web.data.server;

/**
 * Связь профиля с сервером, из которой выводятся его игровые роли на сервере.
 *
 * @param isMember   хотя бы один игрок профиля состоит на сервере ({@code minecraft_server_player})
 * @param isOperator хотя бы один игрок профиля — оператор сервера ({@code is_operator})
 * @param isOwner    профиль — владелец сервера ({@code minecraft_server.owner_profile_id})
 */
public record ServerMembership(boolean isMember, boolean isOperator, boolean isOwner) {
}

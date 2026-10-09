package com.pixplaze.api.web.data.dto;

import com.pixplaze.api.ext.data.player.MinecraftPlayerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerInfo;

import java.util.List;

public record UserInfo(
        ProfileInfo profile,
        List<MinecraftPlayerInfo> players,
        List<MinecraftServerInfo> servers
) {
}

package com.pixplaze.api.web.data.dto;

import com.pixplaze.api.ext.data.player.MinecraftPlayerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.web.data.db.tables.pojos.Profile;

import java.util.List;

public record ProfileInfo(
        Profile profile,
        List<MinecraftPlayerInfo> players,
        List<MinecraftServerInfo> servers
) {
}

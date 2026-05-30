package com.pixplaze.api.web.data.server;

import com.pixplaze.api.ext.data.player.MinecraftPlayerListInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import lombok.Data;

@Data
public class MinecraftServerState {
    private Double tps;
    private Long ping;
    private Long uptime;
    private MinecraftServerStateInfo.StateCode state;
    private MinecraftPlayerListInfo players;
}

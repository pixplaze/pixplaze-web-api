package com.pixplaze.api.web.data.server;

import com.pixplaze.api.ext.data.server.MinecraftServerPortsInfo;
import lombok.Data;

import java.util.List;

@Data
public class RawMinecraftServer {
    private String host;
    private String motd;
    private Boolean license;
    private String favicon;
    private MinecraftServerCore core;
    private MinecraftServerState state;
    private MinecraftServerPortsInfo ports;
    private List<String> plugins;
}

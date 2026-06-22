package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.ext.data.auth.AuthorizationTokenInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.web.data.auth.DeviceAuthorizationSession;
import com.pixplaze.api.web.service.MinecraftServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class MinecraftServerAuthorizationStrategy implements DeviceAuthorizationStrategy<MinecraftServerInfo, AuthorizationTokenInfo> {
    private final JsonMapper jsonMapper;
    private final MinecraftServerService minecraftServerService;

    @Override
    public MinecraftServerInfo parse(String authorizationDetails) {
        return jsonMapper.readValue(authorizationDetails, MinecraftServerInfo.class);
    }

    @Override
    public DeviceAuthorizationResponse<AuthorizationTokenInfo> grant(DeviceAuthorizationSession<MinecraftServerInfo> deviceAuthorizationSession) {
        return null;
    }
}

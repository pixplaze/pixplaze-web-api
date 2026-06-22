package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.ext.data.auth.AuthorizationTokenInfo;
import com.pixplaze.api.ext.data.player.MinecraftPlayerInfo;
import com.pixplaze.api.web.data.auth.DeviceAuthorizationSession;
import com.pixplaze.api.web.exception.MinecraftPlayerAlreadyOwnedException;
import com.pixplaze.api.web.mapper.MinecraftPlayerMapper;
import com.pixplaze.api.web.service.MinecraftPlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class MinecraftPlayerAuthorizationStrategy implements DeviceAuthorizationStrategy<MinecraftPlayerInfo, AuthorizationTokenInfo> {
    private final MinecraftPlayerService minecraftPlayerService;
    private final MinecraftPlayerMapper minecraftPlayerMapper;
    private final JsonMapper jsonMapper;

    @Override
    public MinecraftPlayerInfo parseAuthorizationDetails(String authorizationDetails) {
        return jsonMapper.readValue(authorizationDetails, MinecraftPlayerInfo.class);
    }

    @Override
    public DeviceAuthorizationResponse<AuthorizationTokenInfo> grant(DeviceAuthorizationSession<MinecraftPlayerInfo> session) {
        final var sessionState = session.getState();
//        final var authorizedAuthority = Authority.as(sessionState.authority())
//                .grant(
//                        "minecraft.server.chat.read",
//                        "minecraft.server.chat.write"
//                );
        final var profile = sessionState.profile().orElseThrow();
        final var minecraftPlayerInfo = sessionState.details().orElseThrow();

        try {
            minecraftPlayerService.createIfNotExist(minecraftPlayerMapper.toEntity(minecraftPlayerInfo));
            minecraftPlayerService.linkWithProfile(profile.getId(), minecraftPlayerInfo.uuid());
            
            return DeviceAuthorizationResponse.success(null); // TODO:
        } catch (MinecraftPlayerAlreadyOwnedException e) {
            return DeviceAuthorizationResponse.error("access_denied");
        }
    }
}

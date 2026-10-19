package com.pixplaze.api.web.mapper;

import com.pixplaze.api.ext.data.auth.MinecraftServerAuthorizationDetails;
import com.pixplaze.api.ext.data.player.MinecraftPlayerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerHostInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;
import com.pixplaze.api.web.util.AddressUtils;
import com.pixplaze.api.web.util.NullUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface MinecraftServerMapper {

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "bannedAt", ignore = true)
    @Mapping(target = "ownerProfileId", ignore = true)
    MinecraftServer toEntity(MinecraftServerInfo minecraftServerInfo);
    @Mapping(source = "icon", target = "iconBase64")
    MinecraftServerInfo toInfo(MinecraftServer minecraftServer);

    /// Что подтверждающий видит о сервере, приславшем запрос: адрес из запроса (если есть), иконка, MOTD, ядро,
    /// лицензия, игроки и операторы — чтобы чужой сервер было видно.
    default Map<String, Object> toAuthorizationDetails(MinecraftServerAuthorizationDetails authorizationDetails) {
        final var details = new LinkedHashMap<String, Object>();
        final var info = authorizationDetails.minecraftServerInfo();
        if (info == null) {
            return details;
        }
        info.host(MinecraftServerHostInfo.Type.HOST)
                .ifPresent(host -> details.put("host", AddressUtils.hostAndPort(host.address(), host.port())));
        NullUtils.ifPresentConsume(info.iconBase64(), v -> details.put("iconBase64", v));
        NullUtils.ifPresentConsume(info.motd(), v -> details.put("motd", v));
        NullUtils.ifPresentConsume(info.core(), core -> details.put("core", (core.name() + " " + (core.version() == null ? "" : core.version())).trim()));
        NullUtils.ifPresentConsume(info.isLicense(), v -> details.put("isLicense", v));
        final var players = info.state() == null ? null : info.state().players();
        if (players != null) {
            if (players.online() != null && players.max() != null) {
                details.put("players", players.online() + "/" + players.max());
            }
            if (players.operators() != null) {
                details.put("operators", players.operators().stream()
                        .filter(Objects::nonNull)
                        .map(MinecraftPlayerInfo::username)
                        .filter(Objects::nonNull)
                        .toList());
            }
        }
        return details;
    }
}
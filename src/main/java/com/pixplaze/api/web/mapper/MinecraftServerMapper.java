package com.pixplaze.api.web.mapper;

import com.pixplaze.api.ext.data.auth.MinecraftServerAuthorizationDetails;
import com.pixplaze.api.ext.data.server.MinecraftServerHostInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;
import com.pixplaze.api.web.util.AddressUtils;
import com.pixplaze.api.web.util.NullUtils;
import org.jspecify.annotations.Nullable;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.LinkedHashMap;
import java.util.Map;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface MinecraftServerMapper {

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "bannedAt", ignore = true)
    @Mapping(target = "ownerProfileId", ignore = true)
    MinecraftServer toEntity(MinecraftServerInfo minecraftServerInfo);
    MinecraftServerInfo toInfo(MinecraftServer minecraftServer);

    default Map<String, Object> toAuthorizationDetails(MinecraftServerAuthorizationDetails authorizationDetails) {
        final var details = new LinkedHashMap<String, Object>();
        authorizationDetails.minecraftServerInfo().host(MinecraftServerHostInfo.Type.HOST)
                .ifPresent(host -> details.put("host", AddressUtils.hostAndPort(host.address(), host.port())));
        NullUtils.ifPresentConsume(authorizationDetails.minecraftServerInfo().iconBase64(), v -> details.put("iconBase64", v));
        return details;
    }
}
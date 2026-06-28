package com.pixplaze.api.web.mapper;

import com.pixplaze.api.ext.data.auth.MinecraftPlayerAuthorizationDetails;
import com.pixplaze.api.ext.data.auth.MinecraftServerAuthorizationDetails;
import com.pixplaze.api.ext.data.player.MinecraftPlayerInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftPlayer;
import com.pixplaze.api.web.util.NullUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.LinkedHashMap;
import java.util.Map;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE )
public interface MinecraftPlayerMapper {
    @Mapping(target = "skinHeadBase64", source = "skinHead")
    MinecraftPlayerInfo toInfo(MinecraftPlayer minecraftPlayer);
    MinecraftPlayer toEntity(MinecraftPlayerInfo minecraftPlayer);

    @Mapping(target = "skinHead", source = "headBase64")
    MinecraftPlayer toEntity(MinecraftPlayerAuthorizationDetails authorizationDetails);

    default Map<String, Object> toAuthorizationDetails(MinecraftPlayerAuthorizationDetails authorizationDetails) {
        final var details = new LinkedHashMap<String, Object>();
        NullUtils.ifPresentConsume(authorizationDetails.host(), v -> details.put("host", v));
        NullUtils.ifPresentConsume(authorizationDetails.uuid(), v -> details.put("uuid", v));
        NullUtils.ifPresentConsume(authorizationDetails.username(), v -> details.put("username", v));
        NullUtils.ifPresentConsume(authorizationDetails.isOperator(), v -> details.put("isOperator", v));
        NullUtils.ifPresentConsume(authorizationDetails.headBase64(), v -> details.put("skinHeadBase64", v));
        return details;
    }
}

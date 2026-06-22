package com.pixplaze.api.web.mapper;

import com.pixplaze.api.ext.data.player.MinecraftPlayerInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftPlayer;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE )
public interface MinecraftPlayerMapper {
    MinecraftPlayerInfo toInfo(MinecraftPlayer minecraftPlayer);
    MinecraftPlayer toEntity(MinecraftPlayerInfo minecraftPlayer);
}

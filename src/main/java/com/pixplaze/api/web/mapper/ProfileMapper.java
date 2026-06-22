package com.pixplaze.api.web.mapper;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.web.data.db.tables.pojos.Profile;
import com.pixplaze.api.web.data.user.ClientPrincipial;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        imports = {Authority.class}
)
public interface ProfileMapper {
    @Mapping(target = "authority", expression = "java(Authority.as(Authority.Role.USER).from(Authority.Source.APPLICATION_AUTHORIZED_DEVICE).unauthorized())")
    ClientPrincipial toClientPrincipial(Profile profile);
}

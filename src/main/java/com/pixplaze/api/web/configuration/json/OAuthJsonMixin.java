package com.pixplaze.api.web.configuration.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pixplaze.api.ext.data.oauth.DeviceAuthorizationResponse;
import com.pixplaze.api.ext.data.oauth.OAuthErrorResponse;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import tools.jackson.databind.cfg.MapperBuilder;

/// OAuth-ответы из ext-api (кроме токенов — их собирает {@code DeviceResponseMapper}) пишутся в snake_case (RFC 6749 §5, RFC 8628 §3.2) без пустых полей.
/// Сама библиотека JSON-аннотаций не несёт, поэтому формат задаётся миксином на стороне web-api.
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class OAuthJsonMixin {

    private OAuthJsonMixin() {}

    static <B extends MapperBuilder<?, ?>> B register(B builder) {
        builder.addMixIn(DeviceAuthorizationResponse.class, OAuthJsonMixin.class);
        builder.addMixIn(OAuthErrorResponse.class, OAuthJsonMixin.class);
        return builder;
    }
}

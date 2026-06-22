package com.pixplaze.api.web.mapper;

import com.pixplaze.api.ext.data.auth.DeviceResponseInfo;
import org.mapstruct.Mapper;

import java.util.HashMap;

@Mapper(componentModel = "spring")
public interface DeviceResponseMapper {

    // MapStruct не умеет писать произвольные ключи Map через @Mapping
    // (он ищет write accessor у целевого типа), поэтому ключи RFC 8628
    // раскладываем сами в default-методе. HashMap допускает null в
    // необязательном verification_uri_complete.
    default HashMap<String, Object> toRfc8628Map(DeviceResponseInfo deviceResponseInfo) {
        var map = new HashMap<String, Object>();
        map.put("device_code", deviceResponseInfo.deviceCode());
        map.put("user_code", deviceResponseInfo.userCode());
        map.put("expires_in", deviceResponseInfo.expiresIn());
        map.put("interval", deviceResponseInfo.interval());
        map.put("verification_uri", deviceResponseInfo.verificationUri());
        map.put("verification_uri_complete", deviceResponseInfo.verificationUriComplete());
        return map;
    }
}

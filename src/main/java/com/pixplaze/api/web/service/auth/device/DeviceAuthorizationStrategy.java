package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.web.data.auth.DeviceAuthorizationSession;
import org.springframework.stereotype.Component;

@Component
public interface DeviceAuthorizationStrategy<D, T> {
    D parseAuthorizationDetails(String authorizationDetails);
    DeviceAuthorizationResponse<T> grant(DeviceAuthorizationSession<D> deviceAuthorizationSession);
}

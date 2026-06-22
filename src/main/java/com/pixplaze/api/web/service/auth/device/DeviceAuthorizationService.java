package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.ext.data.auth.DeviceResponseInfo;
import com.pixplaze.api.web.data.dto.DeviceAuthorizationDecisionRequestInfo;
import com.pixplaze.api.web.data.user.ClientPrincipial;

public interface DeviceAuthorizationService {
    DeviceResponseInfo authorize(String clientId, String scope, String authorizationDetails);

    DeviceAuthorizationResponse<?> poll(String clientId, String deviceCode);

    void approve(DeviceAuthorizationDecisionRequestInfo deviceAuthorizationDecisionRequestInfo, ClientPrincipial clientPrincipial);
}

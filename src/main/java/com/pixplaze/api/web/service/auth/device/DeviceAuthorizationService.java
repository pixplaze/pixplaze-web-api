package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.ext.data.auth.DeviceResponseInfo;
import com.pixplaze.api.web.data.dto.DeviceAuthorizationDecisionRequest;
import com.pixplaze.api.web.data.dto.DeviceAuthorizationInfo;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;

public interface DeviceAuthorizationService {
    DeviceResponseInfo authorize(String clientId, String scope, String authorizationDetails);

    Object poll(String clientId, String deviceCode);

    void approve(DeviceAuthorizationDecisionRequest deviceAuthorizationDecisionRequest, ApplicationClientPrincipal clientPrincipial);

    /// Информация о подтверждаемой device-авторизации для подтверждающего (AAD) устройства,
    /// найденной по {@code userCode}. Универсальна для всех типов субъекта (RFC 8628 §3.3).
    DeviceAuthorizationInfo getAuthorizationInfo(String userCode, ApplicationClientPrincipal approver);
}

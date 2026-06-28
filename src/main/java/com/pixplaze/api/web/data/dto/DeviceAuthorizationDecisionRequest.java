package com.pixplaze.api.web.data.dto;

import com.pixplaze.api.web.data.auth.DeviceAuthorizationDecision;

public record DeviceAuthorizationDecisionRequest(DeviceAuthorizationDecision decision, String userCode) {}

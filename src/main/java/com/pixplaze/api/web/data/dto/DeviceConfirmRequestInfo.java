package com.pixplaze.api.web.data.dto;

public record DeviceConfirmRequestInfo(
        String userCode,
        String decision
) {
}

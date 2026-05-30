package com.pixplaze.api.web.data;

import java.time.OffsetDateTime;

public record VoucherCode(
    Long id,
    String code,
    String description,
    Type type,
    OffsetDateTime created,
    OffsetDateTime expires,
    Integer activationsLimit,
    Integer activationsRemaining
) {
    public enum Type {
        INVITE,
        PROMO
    }
}

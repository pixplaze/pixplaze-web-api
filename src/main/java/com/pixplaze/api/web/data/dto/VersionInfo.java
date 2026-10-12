package com.pixplaze.api.web.data.dto;

import java.time.Instant;

/// Версия сборки API. Поля {@code null}, если jar собран не Maven'ом (запуск из IDE)
/// или без {@code .git}; {@code tag} — {@code null}, если на коммите нет тега.
public record VersionInfo(
        String commit,
        String tag,
        String version,
        Instant buildTime
) {
}

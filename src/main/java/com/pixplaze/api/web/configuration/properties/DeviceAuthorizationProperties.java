package com.pixplaze.api.web.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Параметры device-flow (RFC 8628) и производные от них величины. Собраны в одно значение, чтобы
 * связанные числа — срок, интервал, бюджет — считались в одном месте и не расходились.
 *
 * @param expiration      срок жизни запроса с момента создания
 * @param pollingInterval минимальный интервал между опросами (RFC 8628 §3.5)
 * @param decisionTtl     сколько запись живёт после решения подтверждающего: одобренный запрос
 *                        (а с ним bearer-QR обратного логина) должен гаснуть в узком окне
 * @param verificationUri страница, на которой человек вводит user-код; адрес без схемы (так он записан в
 *                        PIXPLAZE_WEB_APP_URL) получает https://, а localhost и 127.0.0.1 — http://: RFC 8628
 *                        требует полный URI, и клиент Minecraft открывает по клику только http(s)
 */
@ConfigurationProperties("app.security.auth.device")
public record DeviceAuthorizationProperties(
        Duration expiration,
        Duration pollingInterval,
        @DefaultValue("15s") Duration decisionTtl,
        String verificationUri
) {

    /// Запас бюджета на честного клиента. Попытка списывается и с раннего опроса, поэтому клиент,
    /// повторивший запрос после сетевого таймаута, тратит больше, чем {@code expiration / interval}.
    /// Чтобы исчерпать бюджет с запасом ×2, нужно весь срок опрашивать вдвое чаще интервала, —
    /// это уже не повторы, а нарушение протокола.
    private static final int ATTEMPT_BUDGET_MARGIN = 2;

    public DeviceAuthorizationProperties {
        verificationUri = withScheme(verificationUri);
    }

    static String withScheme(String uri) {
        if (uri == null || uri.isBlank() || uri.contains("://")) {
            return uri;
        }
        final var local = uri.startsWith("localhost") || uri.startsWith("127.0.0.1");
        return (local ? "http://" : "https://") + uri;
    }

    /// Бюджет опросов на запрос. Минимум — одна попытка на случай {@code interval ≥ expiration}.
    public int attemptBudget() {
        final var intervalSeconds = Math.max(1L, pollingInterval.toSeconds());
        final var honestPolls = Math.max(1L, expiration.toSeconds() / intervalSeconds);

        return Math.toIntExact(honestPolls * ATTEMPT_BUDGET_MARGIN);
    }

    public String verificationUriComplete(String userCode) {
        return verificationUri + "?userCode=" + userCode;
    }
}

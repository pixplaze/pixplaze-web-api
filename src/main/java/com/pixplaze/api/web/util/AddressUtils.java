package com.pixplaze.api.web.util;

import java.util.Locale;

public class AddressUtils {
    /// Канонический вид адреса для хранения и сравнения: без пробелов по краям, в нижнем регистре
    /// (DNS-имена регистронезависимы). Единая точка, чтобы уникальность адресов в БД не обходилась регистром.
    /// IP клиента: первый хоп {@code X-Forwarded-For} (если за доверенным прокси), иначе {@code remoteAddr}.
    /// Best-effort — за непрозрачным прокси без XFF вернёт адрес прокси.
    public static String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        final var forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public static String normalizeHost(String address) {
        return address == null ? null : address.strip().toLowerCase(Locale.ROOT);
    }

    /// Адрес для показа человеку и копирования в клиент: {@code address:port}.
    public static String hostAndPort(String address, Integer port) {
        return address + ":" + port;
    }

    /**
     * Сверка IP MC-клиента и веб-одобряющего. Fail-open для несравнимых адресов: жёсткий отказ
     * только когда обе стороны — валидный IPv4 и они различаются. Если хоть одна сторона IPv6 /
     * отсутствует / нестандартного формата (за NAT по IPv4 совпадут публичные адреса, но IPv6
     * выдаётся по-устройству, а за прокси без XFF мы видим адрес прокси) — сверку пропускаем.
     */
    public static boolean isIpv4Same(String first, String second) {
        if (!isIpv4(first) || !isIpv4(second)) {
            return false;
        }

        return first.equals(second);
    }

    /// Адрес этой же машины: {@code localhost}, {@code 127.x.x.x}, {@code ::1}. Такие адреса бывают только в разработке.
    public static boolean isLoopback(String address) {
        final var host = normalizeHost(address);
        if (host == null) {
            return false;
        }
        return host.equals("localhost") || host.equals("::1") || host.equals("[::1]")
                || (isIpv4(host) && host.startsWith("127."));
    }

    public static boolean isIpv4(String value) {
        if (value == null) {
            return false;
        }
        final var parts = value.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (final var part : parts) {
            try {
                final var octet = Integer.parseInt(part);
                if (octet < 0 || octet > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }
}

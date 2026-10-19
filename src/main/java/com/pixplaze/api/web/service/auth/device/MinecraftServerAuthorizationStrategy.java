package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.web.service.auth.device.model.DeviceAuthorizationContext;
import com.pixplaze.api.ext.data.auth.Authority;
import com.pixplaze.api.ext.data.auth.MinecraftServerAuthorizationDetails;
import com.pixplaze.api.ext.data.auth.VerifiableAuthorizationTokenInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerHostInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.ext.data.auth.MinecraftServerTargets;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;
import com.pixplaze.api.web.data.server.MinecraftServerRegistrationStatus;
import com.pixplaze.api.web.data.server.ServerBid;
import com.pixplaze.api.web.data.dto.DeviceAuthorizationInfo;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.web.data.user.MinecraftServerPrincipal;
import com.pixplaze.api.web.exception.MinecraftServerAddressTakenException;
import com.pixplaze.api.web.exception.http.ConflictException;
import com.pixplaze.api.web.exception.http.ForbiddenException;
import com.pixplaze.api.ext.data.oauth.OAuthError;
import com.pixplaze.api.web.exception.auth.DeviceAuthorizationException;
import com.pixplaze.api.web.mapper.MinecraftServerMapper;
import com.pixplaze.api.web.service.MinecraftServerBidService;
import com.pixplaze.api.web.service.MinecraftServerEnrollmentService;
import com.pixplaze.api.web.service.MinecraftServerService;
import com.pixplaze.api.web.service.auth.MinecraftServerAccessTokenService;
import com.pixplaze.api.web.service.auth.RefreshTokenService;
import com.pixplaze.api.web.util.AddressUtils;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MinecraftServerAuthorizationStrategy implements DeviceAuthorizationStrategy<MinecraftServerAuthorizationDetails, VerifiableAuthorizationTokenInfo> {
    private final MinecraftServerService minecraftServerService;
    private final MinecraftServerBidService minecraftServerBidService;
    private final MinecraftServerMapper minecraftServerMapper;
    private final MinecraftServerAccessTokenService minecraftServerAccessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final MinecraftServerEnrollmentService minecraftServerEnrollmentService;
    private final JsonMapper jsonMapper;

    @org.springframework.beans.factory.annotation.Value("${app.url.api.gateway}")
    private String apiGateway;

    @Override
    public DeviceAuthorizationInfo describe(DeviceAuthorizationContext<MinecraftServerAuthorizationDetails> context) {
        final var details = requireDetails(context);
        final var authority = context.authority();
        final var shown = minecraftServerMapper.toAuthorizationDetails(details);
        // Имя и игровой адрес — из заявки (регистрация) или из базы (повторная авторизация), а не от устройства
        final var serverId = details.minecraftServerInfo() == null ? null : details.minecraftServerInfo().id();
        if (serverId == null) {
            minecraftServerBidService.findByCode(details.inviteCode()).ifPresent(bid -> {
                shown.put("name", bid.name());
                if (bid.host() != null) {
                    shown.put("host", AddressUtils.hostAndPort(bid.host(), bid.port()));
                }
                if (bid.ownerName() != null) {
                    shown.put("owner", bid.ownerName());
                }
            });
        } else {
            minecraftServerService.findById(serverId).ifPresent(server -> shown.put("name", server.getName()));
            minecraftServerService.findGameHost(serverId)
                    .ifPresent(host -> shown.put("host", AddressUtils.hostAndPort(host.address(), host.port())));
        }
        if (context.requesterIp() != null) {
            shown.put("requesterIp", context.requesterIp());
        }

        return new DeviceAuthorizationInfo(
                Authority.Role.MINECRAFT_SERVER.name(),
                context.status(),
                authority.source().code(),
                authority.targets(),
                authority.permissions(),
                shown
        );
    }

    @Override
    public MinecraftServerAuthorizationDetails parse(String clientId, Authority authority, String authorizationDetailsString) {
        return jsonMapper.readValue(authorizationDetailsString, MinecraftServerAuthorizationDetails.class);
    }

    /**
     * Предварительная проверка (RFC 8628, этап device-request, до публикации сессии).
     * Регистрация ({@code id == null}): код из MOTD — код открытой заявки или уже зарегистрированного по нему
     * сервера. Повторная авторизация ({@code id != null}): сервер существует и не
     * забанен. Атомарные проверки, смена статуса и погашение кода — в {@link #authorize}.
     */
    @Override
    public void validate(DeviceAuthorizationContext<MinecraftServerAuthorizationDetails> context) {
        final var authorizationDetails = requireDetails(context);
        final var serverInfo = Optional.ofNullable(authorizationDetails.minecraftServerInfo())
                .orElseThrow(() -> exceptionInvalidRequest("minecraftServerInfo is missing"));

        if (serverInfo.id() == null) {
            final var bid = findBid(authorizationDetails.inviteCode());
            validateHosts(withBidHost(serverInfo, bid).hosts());
            return;
        }

        validateAuthorizationDetails(authorizationDetails);
        final var minecraftServer = minecraftServerService.findById(serverInfo.id())
                .orElseThrow(() -> exceptionAccessDenied("server #%d does not exist".formatted(serverInfo.id())));
        if (minecraftServer.getBannedAt() != null) {
            throw exceptionAccessDenied("server #%d is banned".formatted(serverInfo.id()));
        }
    }

    @Override
    @Transactional
    public VerifiableAuthorizationTokenInfo authorize(DeviceAuthorizationContext<MinecraftServerAuthorizationDetails> context) {
        final var authorizationDetails = requireDetails(context);
        final var minecraftServer = authorize(authorizationDetails, context.approver());

        // Субъектный принципал появляется только здесь — после успешного резолва сервера.
        final var subjectPrincipal = new MinecraftServerPrincipal();
        subjectPrincipal.setMinecraftServerId(minecraftServer.getId());
        subjectPrincipal.setName(minecraftServer.getName());
        // aud = [gateway, сервер]: серверный токен ходит и в BFF, и валидируется самим сервером (targets == aud).
        subjectPrincipal.setAuthority(Authority.as(context.authority())
                .to(apiGateway, MinecraftServerTargets.of(minecraftServer.getId()))
                .grant());

        final var accessToken = minecraftServerAccessTokenService.issue(subjectPrincipal);
        final var refreshToken = refreshTokenService.issue(subjectPrincipal);
        final var publicKey = minecraftServerAccessTokenService.getPublicKeyBase64();

        return new VerifiableAuthorizationTokenInfo(accessToken, refreshToken, publicKey);
    }

    private MinecraftServerAuthorizationDetails requireDetails(DeviceAuthorizationContext<MinecraftServerAuthorizationDetails> context) {
        return Optional.ofNullable(context.details()).orElseThrow(() -> exceptionInvalidRequest("authorization_details are missing"));
    }

    private MinecraftServer authorize(MinecraftServerAuthorizationDetails authorizationDetails, ApplicationClientPrincipal clientPrincipial) {
        if (authorizationDetails.minecraftServerInfo().id() == null) {
            return registrateMinecraftServer(authorizationDetails, clientPrincipial);
        }

        return authorizeMinecraftServer(authorizationDetails, clientPrincipial);
    }

    /// Регистрация по коду из MOTD — в {@link MinecraftServerEnrollmentService#connectPlugin}; здесь только перевод
    /// доменных отказов в OAuth: занятый адрес — {@code invalid_request}, прочее — {@code access_denied}.
    private MinecraftServer registrateMinecraftServer(MinecraftServerAuthorizationDetails authorizationDetails, ApplicationClientPrincipal clientPrincipial) {
        final var minecraftServerBid = findBid(authorizationDetails.inviteCode());
        // Адрес игры — из заявки: его ввёл владелец, а плагин за NAT или прокси своего внешнего адреса не знает
        final var minecraftServerInfo = withBidHost(authorizationDetails.minecraftServerInfo(), minecraftServerBid);
        validateHosts(minecraftServerInfo.hosts());

        try {
            return minecraftServerEnrollmentService.connectPlugin(minecraftServerBid, minecraftServerInfo, clientPrincipial.getId());
        } catch (MinecraftServerAddressTakenException e) {
            throw new DeviceAuthorizationException(OAuthError.INVALID_REQUEST, "the server address is taken by another server", e);
        } catch (ConflictException | ForbiddenException e) {
            throw new DeviceAuthorizationException(OAuthError.ACCESS_DENIED,
                    "registration by bid #%d approved by profile #%d refused".formatted(minecraftServerBid.serverId(), clientPrincipial.getId()), e);
        }
    }

    /// Заявка по коду из MOTD: открытая или уже зарегистрированная по этому коду (пинг успел раньше плагина).
    /// Код публичен, поэтому сам он ничего не даёт — регистрацию одобряет владелец заявки.
    private ServerBid findBid(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw exceptionInvalidRequest("inviteCode is missing");
        }
        return minecraftServerBidService.findByCode(inviteCode)
                .filter(bid -> bid.status() == MinecraftServerRegistrationStatus.PENDING
                        || bid.status() == MinecraftServerRegistrationStatus.ACTIVE)
                .filter(bid -> bid.host() != null)
                .orElseThrow(() -> exceptionAccessDenied("no open bid for invite code '%s'".formatted(inviteCode)));
    }

    /// Адреса для регистрации: игровой (HOST) — из заявки, присланный плагином игнорируется; у остальных
    /// (API, MAP) без адреса — адрес из заявки, порт — присланный.
    private static MinecraftServerInfo withBidHost(MinecraftServerInfo serverInfo, ServerBid bid) {
        final var hosts = new ArrayList<MinecraftServerHostInfo>();
        hosts.add(MinecraftServerHostInfo.server(bid.host(), bid.port()));
        Optional.ofNullable(serverInfo.hosts()).orElse(List.of()).stream()
                .filter(host -> host != null && host.type() != MinecraftServerHostInfo.Type.HOST)
                .map(host -> host.address() == null || host.address().isBlank()
                        ? new MinecraftServerHostInfo(null, bid.host(), host.port(), host.type())
                        : host)
                .forEach(hosts::add);
        return MinecraftServerInfo.builder(serverInfo).hosts(hosts).build();
    }

    /// Повторная авторизация: подтверждать вправе владелец сервера или любой оператор; забаненный — отказ; адреса API и карты и
    /// online-mode синхронизируются с присланными. Игровой адрес (HOST) от плагина игнорируется: его ввёл владелец
    /// в заявке, а плагин за NAT или прокси может определить его неверно — меняется он только через web-app.
    private MinecraftServer authorizeMinecraftServer(MinecraftServerAuthorizationDetails authorizationDetails, ApplicationClientPrincipal approver) {
        final var requestedId = authorizationDetails.minecraftServerInfo().id();
        final var minecraftServer = minecraftServerService.findById(requestedId)
                .orElseThrow(() -> exceptionAccessDenied("server #%d does not exist".formatted(requestedId)));
        final var minecraftServerId = minecraftServer.getId();

        if (minecraftServer.getBannedAt() != null) {
            throw exceptionAccessDenied("server #%d is banned".formatted(minecraftServerId));
        }
        // Владелец мог ещё не привязать своего игрока: при регистрации игрок не привязывается
        if (!approver.getId().equals(minecraftServer.getOwnerProfileId())
                && !minecraftServerService.isPlayerProfileServerOperator(approver.getId(), minecraftServerId)) {
            throw exceptionAccessDenied("approver profile #%d is neither the owner nor an operator of server #%d".formatted(approver.getId(), minecraftServerId));
        }

        try {
            minecraftServerService.upsertHosts(minecraftServerId, withoutGameHost(authorizationDetails.minecraftServerInfo().hosts()));
        } catch (DuplicateKeyException e) {
            // Новый игровой адрес уже занят другим сервером.
            throw new DeviceAuthorizationException(OAuthError.INVALID_REQUEST, "a new address of server #%d is taken by another server".formatted(minecraftServerId), e);
        }
        minecraftServerService.updateLicense(minecraftServerId, authorizationDetails.minecraftServerInfo().isLicense());

        return minecraftServer;
    }

    private static List<MinecraftServerHostInfo> withoutGameHost(List<MinecraftServerHostInfo> hosts) {
        return hosts == null ? null : hosts.stream()
                .filter(host -> host.type() != MinecraftServerHostInfo.Type.HOST)
                .toList();
    }

    private MinecraftServerAuthorizationDetails validateAuthorizationDetails(MinecraftServerAuthorizationDetails minecraftServerAuthorizationDetails) {
        try {
            Objects.requireNonNull(minecraftServerAuthorizationDetails, "'authorizationDetails' must not be null!");
            Objects.requireNonNull(minecraftServerAuthorizationDetails.minecraftServerInfo(), "'authorizationDetails.minecraftServerInfo' must not be null!");
            Objects.requireNonNull(minecraftServerAuthorizationDetails.minecraftServerInfo().id(), "'authorizationDetails.minecraftServerInfo.id' must not be null!");

            // Адреса при повторной авторизации необязательны: присланные перезаписывают сохранённые.
            if (minecraftServerAuthorizationDetails.minecraftServerInfo().hosts() != null) {
                validateHosts(minecraftServerAuthorizationDetails.minecraftServerInfo().hosts());
            }
        } catch (NullPointerException e) {
            throw new DeviceAuthorizationException(OAuthError.INVALID_REQUEST, "a required re-authorization field is missing", e);
        }

        return minecraftServerAuthorizationDetails;
    }

    /// Каждая запись полная, порт в диапазоне, тип не повторяется — иначе ограничения БД дали бы 500.
    private static void validateHosts(List<MinecraftServerHostInfo> hosts) {
        final var types = EnumSet.noneOf(MinecraftServerHostInfo.Type.class);
        for (final var host : hosts) {
            Objects.requireNonNull(host, "'authorizationDetails.minecraftServerInfo.hosts[]' must not be null!");
            final var type = Objects.requireNonNull(host.type(), "'hosts[].type' must not be null!");
            final var address = Objects.requireNonNull(host.address(), "'hosts[].address' must not be null!");
            final var port = Objects.requireNonNull(host.port(), "'hosts[].port' must not be null!");

            if (address.isBlank() || port < 1 || port > 65535 || !types.add(type)) {
                throw new DeviceAuthorizationException(OAuthError.INVALID_REQUEST,
                        "invalid or duplicate host %s '%s:%d'".formatted(type, address, port));
            }
        }
    }

    private @NonNull DeviceAuthorizationException exceptionInvalidRequest(String reason) {
        return new DeviceAuthorizationException(OAuthError.INVALID_REQUEST, reason);
    }

    private @NonNull DeviceAuthorizationException exceptionAccessDenied(String reason) {
        return new DeviceAuthorizationException(OAuthError.ACCESS_DENIED, reason);
    }
}

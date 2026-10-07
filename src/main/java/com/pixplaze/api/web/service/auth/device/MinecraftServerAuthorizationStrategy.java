package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.web.service.auth.device.model.DeviceAuthorizationContext;
import com.pixplaze.api.ext.data.auth.Authority;
import com.pixplaze.api.ext.data.auth.MinecraftServerAuthorizationDetails;
import com.pixplaze.api.ext.data.auth.VerifiableAuthorizationTokenInfo;
import com.pixplaze.api.ext.data.player.MinecraftPlayerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerHostInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.ext.data.auth.MinecraftServerTargets;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServerBid;
import com.pixplaze.api.web.data.db.tables.pojos.VoucherCode;
import com.pixplaze.api.web.data.dto.DeviceAuthorizationInfo;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.web.data.user.MinecraftServerPrincipal;
import com.pixplaze.api.web.data.voucher.VoucherCodeType;
import com.pixplaze.api.web.exception.MinecraftPlayerAlreadyOwnedException;
import com.pixplaze.api.ext.data.oauth.OAuthError;
import com.pixplaze.api.web.exception.auth.DeviceAuthorizationException;
import com.pixplaze.api.web.exception.voucher.VoucherCodeValidationException;
import com.pixplaze.api.web.mapper.MinecraftPlayerMapper;
import com.pixplaze.api.web.mapper.MinecraftServerMapper;
import com.pixplaze.api.web.service.MinecraftPlayerService;
import com.pixplaze.api.web.service.MinecraftServerBidService;
import com.pixplaze.api.web.service.MinecraftServerService;
import com.pixplaze.api.web.service.VoucherCodeService;
import com.pixplaze.api.web.service.auth.MinecraftServerAccessTokenService;
import com.pixplaze.api.web.service.auth.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class MinecraftServerAuthorizationStrategy implements DeviceAuthorizationStrategy<MinecraftServerAuthorizationDetails, VerifiableAuthorizationTokenInfo> {
    private final MinecraftServerService minecraftServerService;
    private final MinecraftServerBidService minecraftServerBidService;
    private final MinecraftPlayerService minecraftPlayerService;
    private final MinecraftServerMapper minecraftServerMapper;
    private final VoucherCodeService voucherCodeService;
    private final MinecraftServerAccessTokenService minecraftServerAccessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final MinecraftPlayerMapper minecraftPlayerMapper;
    private final JsonMapper jsonMapper;

    @org.springframework.beans.factory.annotation.Value("${app.url.api.gateway}")
    private String apiGateway;

    @Override
    public DeviceAuthorizationInfo describe(DeviceAuthorizationContext<MinecraftServerAuthorizationDetails> context) {
        final var details = requireDetails(context);
        final var authority = context.authority();

        return new DeviceAuthorizationInfo(
                Authority.Role.MINECRAFT_SERVER.name(),
                context.status(),
                authority.source().code(),
                authority.targets(),
                authority.permissions(),
                minecraftServerMapper.toAuthorizationDetails(details)
        );
    }

    @Override
    public MinecraftServerAuthorizationDetails parse(String clientId, Authority authority, String authorizationDetailsString) {
        return jsonMapper.readValue(authorizationDetailsString, MinecraftServerAuthorizationDetails.class);
    }

    /**
     * Предварительная проверка (RFC 8628, этап device-request, до публикации сессии).
     * Регистрация ({@code id == null}): по игровому адресу (HOST) есть открытая заявка (bid) и
     * enrollment-ваучер валиден. Повторная авторизация ({@code id != null}): сервер существует и не
     * забанен. Атомарные проверки, создание записей и потребление ваучера — в {@link #authorize}.
     */
    @Override
    public void validate(DeviceAuthorizationContext<MinecraftServerAuthorizationDetails> context) {
        final var authorizationDetails = requireDetails(context);
        final var serverInfo = Optional.ofNullable(authorizationDetails.minecraftServerInfo()).orElseThrow(this::exceptionInvalidRequest);

        if (serverInfo.id() == null) {
            validateRegistrationDetails(authorizationDetails);
            validateEnrollmentVoucher(authorizationDetails.inviteCode());
            findPendingBid(serverInfo).orElseThrow(this::exceptionAccessDenied);
            return;
        }

        validateAuthorizationDetails(authorizationDetails);
        final var minecraftServer = minecraftServerService.findById(serverInfo.id())
                .orElseThrow(this::exceptionAccessDenied);
        if (minecraftServer.getBannedAt() != null) {
            throw exceptionAccessDenied();
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
        return Optional.ofNullable(context.details()).orElseThrow(this::exceptionInvalidRequest);
    }

    private MinecraftServer authorize(MinecraftServerAuthorizationDetails authorizationDetails, ApplicationClientPrincipal clientPrincipial) {
        if (authorizationDetails.minecraftServerInfo().id() == null) {
            return registrateMinecraftServer(authorizationDetails, clientPrincipial);
        }

        return authorizeMinecraftServer(authorizationDetails, clientPrincipial);
    }

    /// Регистрация: по заявке создаём сервер с адресами и записи об игроках, помечаем владельца,
    /// связываем его игрока с профилем одобряющего, гасим ваучер и закрываем заявку.
    private MinecraftServer registrateMinecraftServer(MinecraftServerAuthorizationDetails authorizationDetails, ApplicationClientPrincipal clientPrincipial) {
        final var minecraftServerInfo = authorizationDetails.minecraftServerInfo();
        final var minecraftServerBid = findPendingBid(minecraftServerInfo).orElseThrow(this::exceptionAccessDenied);
        final var voucher = validateEnrollmentVoucher(authorizationDetails.inviteCode());

        // Код из конфига должен соответствовать выданному в заявке и быть привязан к одобряющему владельцу.
        if (!voucher.getId().equals(minecraftServerBid.getVoucherCodeId()) || !voucherCodeService.isBoundTo(voucher.getId(), clientPrincipial.getId())) {
            throw exceptionAccessDenied();
        }

        final var playerList = minecraftServerInfo.state().players();
        final var minecraftServerOperators = playerList.operators();
        final var minecraftServerPlayers = Stream.concat(playerList.operators().stream(), playerList.list().stream())
                .filter(Objects::nonNull)
                .distinct()
                .map(minecraftPlayerMapper::toEntity)
                .collect(Collectors.toList());
        // Владелец — оператор с ником из заявки; без него регистрация не проходит.
        final var minecraftServerOwner = minecraftServerOperators.stream()
                .filter(isMinecraftServerOwner(minecraftServerBid))
                .findFirst()
                .orElseThrow(this::exceptionAccessDenied);
        final var minecraftServer = minecraftServerMapper.toEntity(minecraftServerInfo)
                .setName(minecraftServerBid.getName())
                .setOwnerProfileId(minecraftServerBid.getProfileId());
        final var server = createMinecraftServer(minecraftServer, minecraftServerInfo.hosts());

        minecraftPlayerService.createAll(minecraftServerPlayers);
        minecraftServerService.linkOperators(server.getId(), minecraftServerOperators.stream().map(MinecraftPlayerInfo::uuid).toList(), minecraftServerOwner.uuid());

        // Профиль владельца связываем с его MC-игроком, чтобы он сразу мог делать re-auth как оператор.
        try {
            minecraftPlayerService.linkProfile(minecraftServerOwner.uuid(), clientPrincipial.getId());
        } catch (MinecraftPlayerAlreadyOwnedException e) {
            throw new DeviceAuthorizationException(OAuthError.ACCESS_DENIED, e);
        }

        try {
            voucherCodeService.activate(voucher, clientPrincipial.getId());
        } catch (VoucherCodeValidationException e) {
            throw new DeviceAuthorizationException(OAuthError.ACCESS_DENIED, e);
        }

        // Заявку могли успеть отклонить или она истекла, пока сервер ждал подтверждения.
        if (!minecraftServerBidService.approve(minecraftServerBid.getId())) {
            throw exceptionAccessDenied();
        }

        return server;
    }

    private MinecraftServer createMinecraftServer(MinecraftServer minecraftServer, List<MinecraftServerHostInfo> hosts) {
        try {
            return minecraftServerService.create(minecraftServer, MinecraftServerStateInfo.IntegrationStatus.PLUGIN, hosts);
        } catch (DuplicateKeyException e) {
            // Игровой адрес уже занят другим сервером.
            throw new DeviceAuthorizationException(OAuthError.INVALID_REQUEST, e);
        }
    }

    /// Открытая заявка с плагином на игровой адрес (HOST) из деталей. Заявка без плагина плагином не
    /// регистрируется: её код публичен (стоит в MOTD), а владельца среди операторов искать не по чему.
    private Optional<MinecraftServerBid> findPendingBid(MinecraftServerInfo minecraftServerInfo) {
        return minecraftServerInfo.host(MinecraftServerHostInfo.Type.HOST)
                .flatMap(host -> minecraftServerBidService.findPendingByAddress(host.address(), host.port()))
                .filter(bid -> bid.getIntegration() == MinecraftServerStateInfo.IntegrationStatus.PLUGIN);
    }

    private static @NonNull Predicate<MinecraftPlayerInfo> isMinecraftServerOwner(MinecraftServerBid minecraftServerBid) {
        // Ники Minecraft уникальны без учёта регистра.
        return operator -> minecraftServerBid.getOwnerUsername().equalsIgnoreCase(operator.username());
    }

    /// Повторная авторизация: подтверждать вправе любой оператор; забаненный — отказ; адреса и
    /// online-mode синхронизируются с присланными.
    private MinecraftServer authorizeMinecraftServer(MinecraftServerAuthorizationDetails authorizationDetails, ApplicationClientPrincipal approver) {
        final var minecraftServer = minecraftServerService.findById(authorizationDetails.minecraftServerInfo().id())
                .orElseThrow(this::exceptionAccessDenied);
        final var minecraftServerId = minecraftServer.getId();

        if (minecraftServer.getBannedAt() != null || !minecraftServerService.isPlayerProfileServerOperator(approver.getId(), minecraftServerId)) {
            throw exceptionAccessDenied();
        }

        try {
            minecraftServerService.upsertHosts(minecraftServerId, authorizationDetails.minecraftServerInfo().hosts());
        } catch (DuplicateKeyException e) {
            // Новый игровой адрес уже занят другим сервером.
            throw new DeviceAuthorizationException(OAuthError.INVALID_REQUEST, e);
        }
        minecraftServerService.updateLicense(minecraftServerId, authorizationDetails.minecraftServerInfo().isLicense());

        return minecraftServer;
    }

    private VoucherCode validateEnrollmentVoucher(String inviteCode) {
        try {
            return voucherCodeService.load(inviteCode, VoucherCodeType.INVITE_MINECRAFT_SERVER);
        } catch (VoucherCodeValidationException e) {
            throw new DeviceAuthorizationException(OAuthError.ACCESS_DENIED, e);
        }
    }

    private MinecraftServerAuthorizationDetails validateRegistrationDetails(MinecraftServerAuthorizationDetails minecraftServerAuthorizationDetails) {
        try {
            Objects.requireNonNull(minecraftServerAuthorizationDetails, "'authorizationDetails' must not be null!");
            Objects.requireNonNull(minecraftServerAuthorizationDetails.minecraftServerInfo(), "'authorizationDetails.minecraftServerInfo' must not be null!");
            Objects.requireNonNull(minecraftServerAuthorizationDetails.minecraftServerInfo().iconBase64(), "'authorizationDetails.minecraftServerInfo.iconBase64' must not be null!");
            Objects.requireNonNull(minecraftServerAuthorizationDetails.minecraftServerInfo().hosts(), "'authorizationDetails.minecraftServerInfo.hosts' must not be null!");
            Objects.requireNonNull(minecraftServerAuthorizationDetails.minecraftServerInfo().state(), "'authorizationDetails.minecraftServerInfo.state' must not be null!");
            Objects.requireNonNull(minecraftServerAuthorizationDetails.minecraftServerInfo().state().players(), "'authorizationDetails.minecraftServerInfo.state.players' must not be null!");
            Objects.requireNonNull(minecraftServerAuthorizationDetails.minecraftServerInfo().state().players().list(), "'authorizationDetails.minecraftServerInfo.state.players.list' must not be null!");

            validateHosts(minecraftServerAuthorizationDetails.minecraftServerInfo().hosts());
            if (minecraftServerAuthorizationDetails.minecraftServerInfo().host(MinecraftServerHostInfo.Type.HOST).isEmpty()) {
                throw new DeviceAuthorizationException(OAuthError.INVALID_REQUEST);
            }

            final var operators = Objects.requireNonNull(minecraftServerAuthorizationDetails.minecraftServerInfo().state().players().operators());

            if (operators.isEmpty()) {
                throw new DeviceAuthorizationException(OAuthError.INVALID_REQUEST);
            }
        } catch (NullPointerException e) {
            throw new DeviceAuthorizationException(OAuthError.INVALID_REQUEST, e);
        }

        return minecraftServerAuthorizationDetails;
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
            throw new DeviceAuthorizationException(OAuthError.INVALID_REQUEST, e);
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
                throw new DeviceAuthorizationException(OAuthError.INVALID_REQUEST);
            }
        }
    }

    private @NonNull DeviceAuthorizationException exceptionInvalidRequest() {
        return new DeviceAuthorizationException(OAuthError.INVALID_REQUEST);
    }

    private @NonNull DeviceAuthorizationException exceptionAccessDenied() {
        return new DeviceAuthorizationException(OAuthError.ACCESS_DENIED);
    }
}

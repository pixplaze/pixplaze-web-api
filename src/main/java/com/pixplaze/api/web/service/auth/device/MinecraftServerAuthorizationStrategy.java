package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.ext.data.auth.MinecraftServerAuthorizationDetails;
import com.pixplaze.api.ext.data.auth.VerifiableAuthorizationTokenInfo;
import com.pixplaze.api.ext.data.player.MinecraftPlayerInfo;
import com.pixplaze.api.web.data.auth.DeviceAuthorizationSession;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServerBid;
import com.pixplaze.api.web.data.db.tables.pojos.VoucherCode;
import com.pixplaze.api.web.data.dto.DeviceAuthorizationInfo;
import com.pixplaze.api.web.data.server.MinecraftServerStatus;
import com.pixplaze.api.web.data.user.MinecraftServerPrincipal;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.web.data.voucher.VoucherCodeType;
import com.pixplaze.api.web.exception.auth.DeviceAuthorizationError;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Objects;
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
    public DeviceAuthorizationInfo describe(DeviceAuthorizationSession<MinecraftServerAuthorizationDetails> session) {
        final var sessionState = session.getState();
        final var authorizationDetails = sessionState.authorizationDetails().orElseThrow(DeviceAuthorizationException::new);
        final var status = sessionState.status();
        final var authority = sessionState.authority();

        return new DeviceAuthorizationInfo(
                Authority.Role.MINECRAFT_SERVER.name(),
                status,
                authority.source().code(),
                authority.targets(),
                authority.permissions(),
                minecraftServerMapper.toAuthorizationDetails(authorizationDetails)
        );
    }

    @Override
    public MinecraftServerAuthorizationDetails parse(String clientId, Authority authority, String authorizationDetailsString) {
        return jsonMapper.readValue(authorizationDetailsString, MinecraftServerAuthorizationDetails.class);
    }

    /**
     * Предварительная проверка (RFC 8628, этап device-request, до публикации сессии).
     * Регистрация ({@code id == null}): по host существует заявка (bid) и enrollment-ваучер
     * валиден. Повторная авторизация ({@code id != null}): сервер существует и не забанен.
     * Атомарные проверки, создание записей и потребление ваучера — в {@link #authorize}.
     */
    @Override
    public void validate(DeviceAuthorizationSession<MinecraftServerAuthorizationDetails> session) {
        final var authorizationDetails = session.getState().authorizationDetails().orElseThrow(NullPointerException::new);
        final var serverInfo = Objects.requireNonNull(authorizationDetails.minecraftServerInfo());
        final var minecraftServerHost = Objects.requireNonNull(serverInfo.host());

        if (serverInfo.id() == null) {
            validateRegistrationDetails(authorizationDetails);
            validateEnrollmentVoucher(authorizationDetails.inviteCode());
            minecraftServerBidService.findByHost(minecraftServerHost)
                    .orElseThrow(this::exceptionAccessDenied);
            return;
        }

        validateAuthorizationDetails(authorizationDetails);
        minecraftServerService.getStatus(serverInfo.id())
                .map(MinecraftServerStatus.BANNED::equals)
                .orElseThrow(this::exceptionAccessDenied);
    }

    @Override
    @Transactional
    public VerifiableAuthorizationTokenInfo authorize(DeviceAuthorizationSession<MinecraftServerAuthorizationDetails> session) {
        final var sessionState = session.getState();
        final var authorizationDetails = sessionState.authorizationDetails().orElseThrow(this::exceptionInvalidRequest);
        final var approverPrincipal = sessionState.profile().orElseThrow(this::exceptionInvalidGrant);

        final var minecraftServer = authorize(authorizationDetails, approverPrincipal);

        // Субъектный принципал появляется только здесь — после успешного резолва сервера.
        final var subjectPrincipal = new MinecraftServerPrincipal();
        subjectPrincipal.setServerId(minecraftServer.getId());
        subjectPrincipal.setName(minecraftServer.getName());
        subjectPrincipal.setHost(minecraftServer.getHost());
        // aud = [gateway, host]: серверный токен ходит и в BFF, и валидируется самим сервером (targets ≡ aud).
        subjectPrincipal.setAuthority(Authority.as(sessionState.authority())
                .to(apiGateway, minecraftServer.getHost())
                .grant());

        final var accessToken = minecraftServerAccessTokenService.issue(subjectPrincipal);
        final var refreshToken = refreshTokenService.issue(subjectPrincipal);
        final var publicKey = minecraftServerAccessTokenService.getPublicKeyBase64();

        return new VerifiableAuthorizationTokenInfo(accessToken, refreshToken, publicKey);
    }

    private MinecraftServer authorize(MinecraftServerAuthorizationDetails authorizationDetails, ApplicationClientPrincipal clientPrincipial) {
        if (authorizationDetails.minecraftServerInfo().id() == null) {
            return registrateMinecraftServer(authorizationDetails, clientPrincipial);
        }

        return authorizeMinecraftServer(authorizationDetails, clientPrincipial);
    }

    /// Регистрация: по заявке создаём сервер и записи об игроках, помечаем владельца, гасим ваучер.
    private MinecraftServer registrateMinecraftServer(MinecraftServerAuthorizationDetails authorizationDetails, ApplicationClientPrincipal clientPrincipial) {
        final var minecraftServerInfo = authorizationDetails.minecraftServerInfo();
        final var minecraftServerHost = minecraftServerInfo.host();
        final var minecraftServerBid = minecraftServerBidService.findByHost(minecraftServerHost).orElseThrow(this::exceptionAccessDenied);
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
        final var minecraftServerOwner = minecraftServerOperators.stream()
                .filter(isMinecraftServerOwner(minecraftServerBid))
                .findFirst()
                .orElseThrow(this::exceptionAccessDenied);
        final var minecraftServer = minecraftServerMapper.toEntity(minecraftServerInfo).setName(minecraftServerBid.getName());
        final var server = minecraftServerService.createActive(minecraftServer);

        minecraftPlayerService.createAll(minecraftServerPlayers);
        minecraftServerService.linkOperators(server.getId(), List.of(minecraftServerOwner.uuid()), minecraftServerOwner.uuid());

        // Профиль владельца связываем с его MC-игроком, чтобы он сразу мог делать re-auth как оператор.
        minecraftPlayerService.linkProfile(minecraftServerOwner.uuid(), clientPrincipial.getId());

        try {
            voucherCodeService.activate(voucher, clientPrincipial.getId());
        } catch (VoucherCodeValidationException e) {
            throw new DeviceAuthorizationException(DeviceAuthorizationError.ACCESS_DENIED, e);
        }

        minecraftServerBidService.delete(minecraftServerBid.getId());

        return server;
    }

    private static @NonNull Predicate<MinecraftPlayerInfo> isMinecraftServerOwner(MinecraftServerBid minecraftServerBid) {
        return operator -> minecraftServerBid.getOwnerUsername().equals(operator.username());
    }

    /// Повторная авторизация: подтверждать вправе любой оператор; забаненный — отказ; host синхронизируется.
    private MinecraftServer authorizeMinecraftServer(MinecraftServerAuthorizationDetails authorizationDetails, ApplicationClientPrincipal approver) {
        final var minecraftServer = minecraftServerService.findById(authorizationDetails.minecraftServerInfo().id())
                .orElseThrow(this::exceptionAccessDenied);
        final var minecraftServerId = minecraftServer.getId();
        final var authorizationDetailsHost = authorizationDetails.minecraftServerInfo().host();

        if (!minecraftServerService.isPlayerProfileServerOperator(approver.getId(), minecraftServerId)) {
            throw exceptionAccessDenied();
        }

        if (authorizationDetailsHost != null && !authorizationDetailsHost.equals(minecraftServer.getHost())) {
            minecraftServerService.updateHost(minecraftServerId, authorizationDetailsHost);
        }

        return minecraftServer;
    }

    private VoucherCode validateEnrollmentVoucher(String inviteCode) {
        try {
            return voucherCodeService.load(inviteCode, VoucherCodeType.INVITE_MINECRAFT_SERVER);
        } catch (VoucherCodeValidationException e) {
            throw new DeviceAuthorizationException(DeviceAuthorizationError.ACCESS_DENIED, e);
        }
    }

    private MinecraftServerAuthorizationDetails validateRegistrationDetails(MinecraftServerAuthorizationDetails minecraftServerAuthorizationDetails) {
        try {
            Objects.requireNonNull(minecraftServerAuthorizationDetails, "'authorizationDetails' must not be null!");
            Objects.requireNonNull(minecraftServerAuthorizationDetails.minecraftServerInfo(), "'authorizationDetails.minecraftServerInfo' must not be null!");
            Objects.requireNonNull(minecraftServerAuthorizationDetails.minecraftServerInfo().iconBase64(), "'authorizationDetails.minecraftServerInfo.iconBase64' must not be null!");
            Objects.requireNonNull(minecraftServerAuthorizationDetails.minecraftServerInfo().host(), "'authorizationDetails.minecraftServerInfo.host' must not be null!");
            Objects.requireNonNull(minecraftServerAuthorizationDetails.minecraftServerInfo().state(), "'authorizationDetails.minecraftServerInfo.state' must not be null!");
            Objects.requireNonNull(minecraftServerAuthorizationDetails.minecraftServerInfo().state().players(), "'authorizationDetails.minecraftServerInfo.state.players' must not be null!");
            Objects.requireNonNull(minecraftServerAuthorizationDetails.minecraftServerInfo().state().players().list(), "'authorizationDetails.minecraftServerInfo.state.players.list' must not be null!");

            final var operators = Objects.requireNonNull(minecraftServerAuthorizationDetails.minecraftServerInfo().state().players().operators());

            if (operators.isEmpty()) {
                throw new DeviceAuthorizationException(DeviceAuthorizationError.INVALID_REQUEST);
            }
        } catch (NullPointerException e) {
            throw new DeviceAuthorizationException(DeviceAuthorizationError.INVALID_REQUEST, e);
        }

        return minecraftServerAuthorizationDetails;
    }

    private MinecraftServerAuthorizationDetails validateAuthorizationDetails(MinecraftServerAuthorizationDetails minecraftServerAuthorizationDetails) {
        try {
            Objects.requireNonNull(minecraftServerAuthorizationDetails, "'authorizationDetails' must not be null!");
            Objects.requireNonNull(minecraftServerAuthorizationDetails.minecraftServerInfo(), "'authorizationDetails.minecraftServerInfo' must not be null!");
            Objects.requireNonNull(minecraftServerAuthorizationDetails.minecraftServerInfo().id(), "'authorizationDetails.minecraftServerInfo.id' must not be null!");
            Objects.requireNonNull(minecraftServerAuthorizationDetails.minecraftServerInfo().host(), "'authorizationDetails.minecraftServerInfo.host' must not be null!");
        } catch (NullPointerException e) {
            throw new DeviceAuthorizationException(DeviceAuthorizationError.INVALID_REQUEST, e);
        }

        return minecraftServerAuthorizationDetails;
    }

    private @NonNull DeviceAuthorizationException exceptionInvalidRequest() {
        return new DeviceAuthorizationException(DeviceAuthorizationError.INVALID_REQUEST);
    }

    private @NonNull DeviceAuthorizationException exceptionInvalidRequest(Exception e) {
        return new DeviceAuthorizationException(DeviceAuthorizationError.INVALID_REQUEST, e);
    }

    private @NonNull DeviceAuthorizationException exceptionAccessDenied() {
        return new DeviceAuthorizationException(DeviceAuthorizationError.ACCESS_DENIED);
    }

    private @NonNull DeviceAuthorizationException exceptionInvalidGrant() {
        return new DeviceAuthorizationException(DeviceAuthorizationError.INVALID_GRANT);
    }
}

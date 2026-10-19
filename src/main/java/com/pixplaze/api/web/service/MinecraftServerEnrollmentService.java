package com.pixplaze.api.web.service;

import com.pixplaze.api.ext.data.player.MinecraftPlayerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;
import com.pixplaze.api.web.data.server.MinecraftServerRegistrationStatus;
import com.pixplaze.api.web.data.server.ServerBid;
import com.pixplaze.api.web.exception.MinecraftServerAddressTakenException;
import com.pixplaze.api.web.exception.http.ConflictException;
import com.pixplaze.api.web.exception.http.ForbiddenException;
import com.pixplaze.api.web.exception.http.NotFoundException;
import com.pixplaze.api.web.mapper.MinecraftPlayerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Регистрация заявки (PENDING → ACTIVE) — единственное место регистрации. Код из MOTD подтверждает пинг
 * ({@link com.pixplaze.api.web.service.server.MinecraftServerBidVerifier}) или админ без кода
 * ({@link #enrollWithNativeIntegration}); плагин — через device-flow с одобрением владельца
 * ({@link com.pixplaze.api.web.service.auth.device.MinecraftServerAuthorizationStrategy} → {@link #connectPlugin}).
 * Бросает только доменные исключения; перевод в OAuth-ошибки — забота стратегии.
 */
@Service
@RequiredArgsConstructor
public class MinecraftServerEnrollmentService {
    private final MinecraftServerBidService minecraftServerBidService;
    private final MinecraftServerService minecraftServerService;
    private final MinecraftPlayerService minecraftPlayerService;
    private final VoucherCodeService voucherCodeService;
    private final MinecraftPlayerMapper minecraftPlayerMapper;

    /// Регистрирует заявку без плагина: код подтверждён пингом или админ одобрил вручную.
    ///
    /// @throws NotFoundException у сервера нет заявки
    /// @throws ConflictException сервер уже не заявка
    @Transactional
    public MinecraftServer enrollWithNativeIntegration(Long serverId) {
        final var bid = minecraftServerBidService.findLatest(serverId)
                .orElseThrow(() -> new NotFoundException("Bid #%d not found!".formatted(serverId)));
        return activate(bid, MinecraftServerStateInfo.IntegrationStatus.NATIVE);
    }

    /**
     * Подключает плагин по коду заявки, когда владелец одобрил device-авторизацию. Открытая заявка
     * регистрируется; уже зарегистрированный сервер (код успел подтвердить пинг) просто получает плагин.
     * Адреса и online-mode — присланные, игроки и операторы — из списка плагина, если он есть.
     *
     * @throws ForbiddenException                   одобряет не владелец или сервер забанен
     * @throws ConflictException                    заявка закрыта (отклонена или истекла)
     * @throws MinecraftServerAddressTakenException адрес занят другим сервером
     */
    @Transactional
    public MinecraftServer connectPlugin(ServerBid bid, MinecraftServerInfo serverInfo, Long approverProfileId) {
        if (!Objects.equals(bid.ownerProfileId(), approverProfileId)) {
            throw new ForbiddenException("Bid #%d is not approved by its owner!".formatted(bid.serverId()));
        }

        final var server = switch (bid.status()) {
            case PENDING -> activate(bid, MinecraftServerStateInfo.IntegrationStatus.PLUGIN);
            case ACTIVE -> minecraftServerService.findById(bid.serverId())
                    .filter(found -> found.getBannedAt() == null)
                    .orElseThrow(() -> new ForbiddenException("Server #%d is banned!".formatted(bid.serverId())));
            case REJECTED, EXPIRED -> throw new ConflictException("Bid #%d is %s!".formatted(bid.serverId(), bid.status()));
        };

        try {
            minecraftServerService.upsertHosts(server.getId(), serverInfo.hosts());
        } catch (DuplicateKeyException e) {
            throw new MinecraftServerAddressTakenException(bid.host(), bid.port());
        }
        minecraftServerService.updateLicense(server.getId(), serverInfo.isLicense());

        // Оператор из ops.json, ни разу не заходивший на сервер, приходит без ника: такого игрока не завести
        final var players = Optional.ofNullable(serverInfo.state()).map(MinecraftServerStateInfo::players);
        final var operators = known(players.map(list -> list.operators()).orElse(List.of()));
        final var online = known(players.map(list -> list.list()).orElse(List.of()));
        minecraftPlayerService.createAll(Stream.concat(operators.stream(), online.stream())
                .distinct()
                .map(minecraftPlayerMapper::toEntity)
                .toList());
        minecraftServerService.linkOperators(server.getId(), operators.stream().map(MinecraftPlayerInfo::uuid).toList());

        return server;
    }

    private static List<MinecraftPlayerInfo> known(List<MinecraftPlayerInfo> players) {
        return players == null ? List.of() : players.stream()
                .filter(player -> player != null && player.uuid() != null
                        && player.username() != null && !player.username().isBlank())
                .toList();
    }

    /// PENDING → ACTIVE и погашение кода владельцем. Статус меняется первым: гонка с отклонением,
    /// истечением или второй регистрацией отсекается до остальных записей.
    private MinecraftServer activate(ServerBid bid, MinecraftServerStateInfo.IntegrationStatus integration) {
        if (bid.status() != MinecraftServerRegistrationStatus.PENDING) {
            throw new ConflictException("Bid #%d is not pending!".formatted(bid.serverId()));
        }
        final var server = minecraftServerService.activate(bid.serverId(), integration)
                .orElseThrow(() -> new ConflictException("Bid #%d is not pending!".formatted(bid.serverId())));

        // Владелец мог удалить профиль: сервер всё равно регистрируется, код больше не нужен — статус не PENDING
        if (bid.ownerProfileId() != null) {
            voucherCodeService.findById(bid.voucherCodeId())
                    .ifPresent(voucher -> voucherCodeService.activate(voucher, bid.ownerProfileId()));
        }
        return server;
    }
}

package com.pixplaze.api.web.service;

import com.pixplaze.api.ext.data.player.MinecraftPlayerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerHostInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServerBid;
import com.pixplaze.api.web.data.db.tables.pojos.VoucherCode;
import com.pixplaze.api.web.data.voucher.VoucherCodeType;
import com.pixplaze.api.web.exception.MinecraftPlayerAlreadyOwnedException;
import com.pixplaze.api.web.exception.MinecraftServerAddressTakenException;
import com.pixplaze.api.web.exception.http.ConflictException;
import com.pixplaze.api.web.exception.http.ForbiddenException;
import com.pixplaze.api.web.mapper.MinecraftPlayerMapper;
import com.pixplaze.api.web.mapper.MinecraftServerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Создание сервера по заявке — единственное место регистрации. Заявку без плагина подтверждает код
 * в MOTD ({@link com.pixplaze.api.web.service.server.MinecraftServerBidVerifier}) или админ без кода
 * ({@link #enrollWithNativeIntegration}); заявку с плагином — сам плагин через device-flow
 * ({@link com.pixplaze.api.web.service.auth.device.MinecraftServerAuthorizationStrategy} → {@link #enrollWithPluginIntegration}).
 * Общие шаги в одном порядке: закрыть заявку, создать сервер, погасить её код. Бросает только доменные
 * исключения; перевод в OAuth-ошибки — забота стратегии.
 */
@Service
@RequiredArgsConstructor
public class MinecraftServerEnrollmentService {
    private final MinecraftServerBidService minecraftServerBidService;
    private final MinecraftServerService minecraftServerService;
    private final MinecraftPlayerService minecraftPlayerService;
    private final VoucherCodeService voucherCodeService;
    private final MinecraftServerMapper minecraftServerMapper;
    private final MinecraftPlayerMapper minecraftPlayerMapper;

    /// Подтверждает открытую NATIVE-заявку: сервер с игровым адресом из заявки, владелец — профиль
    /// заявки. Конфликт — заявка с плагином, уже закрыта (гонка проверки MOTD и админа) или её адрес
    /// успел занять другой сервер.
    @Transactional
    public MinecraftServer enrollWithNativeIntegration(MinecraftServerBid enrollmentBid) {
        if (enrollmentBid.getIntegration() != MinecraftServerStateInfo.IntegrationStatus.NATIVE) {
            throw new ConflictException("Bid #%d is confirmed by the plugin, not approved manually!".formatted(enrollmentBid.getId()));
        }

        final var enrollmentVoucherCode = voucherCodeService.findById(enrollmentBid.getVoucherCodeId())
                .orElseThrow(() -> new IllegalStateException("Bid #%d has no voucher code!".formatted(enrollmentBid.getId())));
        final var minecraftServer = new MinecraftServer()
                .setName(enrollmentBid.getName())
                .setOwnerProfileId(enrollmentBid.getProfileId());

        return enroll(
                enrollmentBid,
                enrollmentVoucherCode,
                minecraftServer,
                MinecraftServerStateInfo.IntegrationStatus.NATIVE,
                List.of(MinecraftServerHostInfo.server(enrollmentBid.getHost(), enrollmentBid.getPort()))
        );
    }

    /**
     * Регистрирует сервер по PLUGIN-заявке, когда владелец одобрил device-авторизацию плагина. Код из
     * конфига плагина должен быть кодом заявки, одобрять — её владелец, а среди операторов сервера
     * должен быть игрок с ником из заявки. Кроме общих шагов создаёт игроков, привязывает операторов
     * (владельца — с is_owner) и связывает игрока-владельца с профилем, чтобы тот сразу мог делать
     * re-auth как оператор.
     *
     * @throws ForbiddenException                    код не тот или недействителен, одобряет не владелец, владельца нет среди операторов
     * @throws MinecraftServerAddressTakenException  игровой адрес уже занят другим сервером
     * @throws ConflictException                     заявка уже закрыта (отклонена или истекла)
     * @throws MinecraftPlayerAlreadyOwnedException  игрок-владелец привязан к другому профилю
     */
    @Transactional
    public MinecraftServer enrollWithPluginIntegration(
            MinecraftServerBid enrollmentBid,
            MinecraftServerInfo serverInfo,
            String inviteCode,
            Long approverProfileId
    ) {
        final var voucher = voucherCodeService.load(inviteCode, VoucherCodeType.INVITE_MINECRAFT_SERVER);
        // Код выдаётся владельцу заявки и привязывается только к нему, поэтому сверки владельца достаточно.
        if (!voucher.getId().equals(enrollmentBid.getVoucherCodeId()) || !enrollmentBid.getProfileId().equals(approverProfileId)) {
            throw new ForbiddenException("Bid #%d is not confirmed by its owner's code!".formatted(enrollmentBid.getId()));
        }

        final var playerList = serverInfo.state().players();
        final var operators = playerList.operators();
        // Ники Minecraft уникальны без учёта регистра.
        final var owner = operators.stream()
                .filter(operator -> enrollmentBid.getOwnerUsername().equalsIgnoreCase(operator.username()))
                .findFirst()
                .orElseThrow(() -> new ForbiddenException("Owner '%s' of bid #%d is not a server operator!".formatted(enrollmentBid.getOwnerUsername(), enrollmentBid.getId())));
        final var minecraftServer = minecraftServerMapper.toEntity(serverInfo)
                .setName(enrollmentBid.getName())
                .setOwnerProfileId(enrollmentBid.getProfileId());

        final var server = enroll(
                enrollmentBid,
                voucher,
                minecraftServer,
                MinecraftServerStateInfo.IntegrationStatus.PLUGIN,
                serverInfo.hosts()
        );

        minecraftPlayerService.createAll(Stream.concat(operators.stream(), playerList.list().stream())
                .filter(Objects::nonNull)
                .distinct()
                .map(minecraftPlayerMapper::toEntity)
                .toList());
        minecraftServerService.linkOperators(server.getId(), operators.stream().map(MinecraftPlayerInfo::uuid).toList(), owner.uuid());
        minecraftPlayerService.linkProfile(owner.uuid(), approverProfileId);

        return server;
    }

    /// Общие шаги регистрации. Заявка закрывается первой: гонка с отклонением или истечением отсекается
    /// до создания записей, а при любой ошибке дальше транзакция откатит и её.
    private MinecraftServer enroll(
            MinecraftServerBid bid,
            VoucherCode voucher,
            MinecraftServer server,
            MinecraftServerStateInfo.IntegrationStatus integration,
            Collection<MinecraftServerHostInfo> hosts
    ) {
        if (!minecraftServerBidService.approve(bid.getId())) {
            throw new ConflictException("Bid #%d is not pending!".formatted(bid.getId()));
        }

        final MinecraftServer created;
        try {
            created = minecraftServerService.create(server, integration, hosts);
        } catch (DuplicateKeyException e) {
            throw new MinecraftServerAddressTakenException(bid.getHost(), bid.getPort());
        }

        voucherCodeService.activate(voucher, bid.getProfileId());
        return created;
    }
}

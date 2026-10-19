package com.pixplaze.api.web.service;

import com.pixplaze.api.ext.data.server.MinecraftServerHostInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.web.data.dto.MinecraftServerBidInfo;
import com.pixplaze.api.web.data.dto.MinecraftServerBidRequest;
import com.pixplaze.api.web.data.dto.MinecraftServerBidResponse;
import com.pixplaze.api.web.data.server.MinecraftServerRegistrationStatus;
import com.pixplaze.api.web.data.server.ServerBid;
import com.pixplaze.api.web.data.voucher.VoucherCodeType;
import com.pixplaze.api.web.exception.MinecraftServerAddressTakenException;
import com.pixplaze.api.web.exception.http.ConflictException;
import com.pixplaze.api.web.repository.MinecraftServerBidRepository;
import com.pixplaze.api.web.repository.MinecraftServerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Заявки на регистрацию: заявка — сервер в статусе PENDING с игровым адресом, код регистрации — одноразовый
 * ваучер, связанный с сервером в {@code minecraft_server_bid}. Регистрация по коду — в
 * {@link MinecraftServerEnrollmentService}.
 */
@Service
@RequiredArgsConstructor
public class MinecraftServerBidService {
    private final MinecraftServerBidRepository minecraftServerBidRepository;
    private final MinecraftServerRepository minecraftServerRepository;
    private final VoucherCodeService voucherCodeService;
    private final MinecraftServerService minecraftServerService;
    private final TransactionTemplate transactionTemplate;

    /// Срок жизни открытой заявки от выдачи последнего кода; после него она закрывается как EXPIRED.
    @Value("${app.servers.bids.ttl:7d}")
    private Duration bidTtl;

    /**
     * Заявка владельца: сервер в статусе PENDING и код для MOTD. Пинг нужен только для иконки в превью —
     * недоступный сервер её просто не получит; идёт до транзакции, чтобы соединение с БД не ждало таймаута.
     */
    public MinecraftServerBidResponse createBid(MinecraftServerBidRequest request, Long ownerProfileId) {
        final var online = minecraftServerService.pingServer(request.host(), request.port());
        final var code = transactionTemplate.execute(status -> issueBid(request.name(), request.host(), request.port(), ownerProfileId));
        final var preview = MinecraftServerInfo.preview(
                request.name(), request.host(), request.port(), online.map(MinecraftServerInfo::iconBase64).orElse(null)
        );
        return new MinecraftServerBidResponse(code, preview);
    }

    /// Создаёт сервер-заявку и выдаёт ему одноразовый код, привязанный к профилю владельца. Код стоит в MOTD
    /// и публичен: сам по себе он ничего не авторизует. Возвращает сырой код.
    private String issueBid(String name, String host, Integer port, Long ownerProfileId) {
        if (minecraftServerRepository.isGameHostTaken(host, port)) {
            throw new MinecraftServerAddressTakenException(host, port);
        }

        final Long serverId;
        try {
            serverId = minecraftServerRepository.createPending(name, ownerProfileId, MinecraftServerHostInfo.server(host, port)).getId();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("The address '%s:%s' is already taken!".formatted(host, port));
        }

        final var voucher = voucherCodeService.issue(VoucherCodeType.INVITE_MINECRAFT_SERVER, 1);
        minecraftServerBidRepository.create(serverId, voucher.getId());
        voucherCodeService.bind(voucher.getId(), ownerProfileId);
        return voucher.getCode();
    }

    /// Заявка по коду регистрации (любого статуса); неизвестный код или код не заявки — пусто.
    public Optional<ServerBid> findByCode(String code) {
        return voucherCodeService.findByCode(code)
                .filter(voucher -> voucher.getType() == VoucherCodeType.INVITE_MINECRAFT_SERVER)
                .flatMap(voucher -> minecraftServerBidRepository.findByVoucherCodeId(voucher.getId()));
    }

    /// Заявка сервера с последним кодом.
    public Optional<ServerBid> findLatest(Long serverId) {
        return minecraftServerBidRepository.findLatest(serverId);
    }

    /// Открытые заявки с последним кодом, старые первыми.
    public List<ServerBid> findPending() {
        return minecraftServerBidRepository.findPending();
    }

    /// Отклоняет открытую заявку; адрес остаётся историей, пока его не займёт другой сервер.
    /// {@code false} — сервер уже не заявка.
    public boolean reject(Long serverId) {
        return minecraftServerRepository.setStatus(serverId, MinecraftServerRegistrationStatus.REJECTED).isPresent();
    }

    /// Закрывает просроченные открытые заявки (EXPIRED); возвращает их число.
    @Transactional
    public int expireOverdue() {
        return minecraftServerBidRepository.expirePendingIssuedBefore(OffsetDateTime.now().minus(bidTtl)).size();
    }

    /// Заявки профиля для страницы «мои заявки», новые первыми.
    public List<MinecraftServerBidInfo> findInfosOf(Long profileId) {
        return toInfos(minecraftServerBidRepository.findLatest(Objects.requireNonNull(profileId), null));
    }

    /// Заявки для админа; {@code status == null} — все.
    public List<MinecraftServerBidInfo> findInfos(MinecraftServerRegistrationStatus status) {
        return toInfos(minecraftServerBidRepository.findLatest(null, status));
    }

    private List<MinecraftServerBidInfo> toInfos(List<ServerBid> bids) {
        return bids.stream()
                .map(bid -> new MinecraftServerBidInfo(
                        bid.serverId(), bid.name(), bid.host(), bid.port(), bid.status(), bid.code(), bid.ownerName(),
                        bid.createdAt(),
                        bid.status() == MinecraftServerRegistrationStatus.PENDING ? bid.createdAt().plus(bidTtl) : null))
                .toList();
    }
}

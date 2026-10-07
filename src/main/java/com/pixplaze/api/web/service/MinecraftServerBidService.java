package com.pixplaze.api.web.service;

import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServerBid;
import com.pixplaze.api.web.data.dto.MinecraftServerBidInfo;
import com.pixplaze.api.web.data.server.MinecraftServerBidStatus;
import com.pixplaze.api.web.data.voucher.VoucherCodeType;
import com.pixplaze.api.web.exception.http.ConflictException;
import com.pixplaze.api.web.repository.MinecraftServerBidRepository;
import com.pixplaze.api.web.repository.MinecraftServerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MinecraftServerBidService {
    private final MinecraftServerBidRepository minecraftServerBidRepository;
    private final MinecraftServerRepository minecraftServerRepository;
    private final VoucherCodeService voucherCodeService;

    /// Срок жизни открытой заявки от создания; после него она закрывается как EXPIRED и освобождает адрес.
    @Value("${app.servers.bids.ttl:7d}")
    private Duration bidTtl;

    public MinecraftServerBid create(MinecraftServerBid bid) {
        return minecraftServerBidRepository.create(bid);
    }

    /**
     * Создаёт заявку владельца и выпускает её одноразовый код, привязанный к профилю владельца.
     * С плагином это enrollment-ваучер для конфига плагина (сервер предъявит его при регистрации),
     * без плагина — код для MOTD, по которому принадлежность сервера подтверждается пингом.
     * Код из MOTD публичен, поэтому сам по себе ничего не авторизует. Возвращает заявку и сырой код.
     */
    @Transactional
    public BidResult createBid(
            String name,
            String host,
            Integer port,
            String ownerUsername,
            MinecraftServerStateInfo.IntegrationStatus integration,
            Long ownerProfileId
    ) {
        // Сервер с этим игровым адресом уже зарегистрирован: регистрация по такой заявке всё равно упадёт.
        if (minecraftServerRepository.isGameHostTaken(host, port)) {
            throw new ConflictException("A server at '%s:%s' is already registered!".formatted(host, port));
        }

        final var voucher = voucherCodeService.issue(VoucherCodeType.INVITE_MINECRAFT_SERVER, 1);

        final MinecraftServerBid minecraftServerBid;
        try {
            minecraftServerBid = create(new MinecraftServerBid()
                    .setName(name)
                    .setHost(host)
                    .setPort(port)
                    .setOwnerUsername(ownerUsername)
                    .setIntegration(integration)
                    .setVoucherCodeId(voucher.getId())
                    .setProfileId(ownerProfileId));
        } catch (DuplicateKeyException e) {
            throw new ConflictException("A bid for '%s:%s' is already pending!".formatted(host, port));
        }

        voucherCodeService.bind(voucher.getId(), ownerProfileId);

        return new BidResult(minecraftServerBid, voucher.getCode());
    }

    /// Открытая заявка на игровой адрес.
    public Optional<MinecraftServerBid> findPendingByAddress(String host, Integer port) {
        return minecraftServerBidRepository.findPendingByAddress(host, port);
    }

    public Optional<MinecraftServerBid> findById(Long id) {
        return minecraftServerBidRepository.findById(id);
    }

    /// Открытые заявки ({@code integration == null} — любые), старые первыми.
    public List<MinecraftServerBid> findPending(MinecraftServerStateInfo.IntegrationStatus integration) {
        return minecraftServerBidRepository.findPending(integration);
    }

    /// Закрывает заявку как одобренную: сервер зарегистрирован, адрес свободен для новых заявок.
    /// {@code false} — заявка уже закрыта.
    public boolean approve(Long id) {
        return minecraftServerBidRepository.setStatus(id, MinecraftServerBidStatus.APPROVED);
    }

    /// Отклоняет открытую заявку. {@code false} — заявка уже закрыта.
    public boolean reject(Long id) {
        return minecraftServerBidRepository.setStatus(id, MinecraftServerBidStatus.REJECTED);
    }

    /// Закрывает просроченные открытые заявки (EXPIRED); возвращает их число.
    public int expireOverdue() {
        return minecraftServerBidRepository.expirePendingCreatedBefore(OffsetDateTime.now().minus(bidTtl));
    }

    /// Заявки профиля для страницы «мои заявки», новые первыми.
    public List<MinecraftServerBidInfo> findInfosOf(Long profileId) {
        return withExpiry(minecraftServerBidRepository.findInfos(Objects.requireNonNull(profileId), null));
    }

    /// Заявки для админа; {@code status == null} — все.
    public List<MinecraftServerBidInfo> findInfos(MinecraftServerBidStatus status) {
        return withExpiry(minecraftServerBidRepository.findInfos(null, status));
    }

    private List<MinecraftServerBidInfo> withExpiry(List<MinecraftServerBidInfo> bids) {
        return bids.stream()
                .map(bid -> bid.status() != MinecraftServerBidStatus.PENDING ? bid : new MinecraftServerBidInfo(
                        bid.id(), bid.name(), bid.host(), bid.port(), bid.integration(), bid.status(), bid.inviteCode(),
                        bid.ownerUsername(), bid.applicant(), bid.createdAt(), bid.createdAt().plus(bidTtl)))
                .toList();
    }

    public record BidResult(MinecraftServerBid bid, String code) {}
}

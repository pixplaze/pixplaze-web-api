package com.pixplaze.api.web.service;

import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServerBid;
import com.pixplaze.api.web.data.voucher.VoucherCodeType;
import com.pixplaze.api.web.repository.MinecraftServerBidRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MinecraftServerBidService {
    private final MinecraftServerBidRepository minecraftServerBidRepository;
    private final VoucherCodeService voucherCodeService;

    public MinecraftServerBid create(MinecraftServerBid bid) {
        return minecraftServerBidRepository.create(bid);
    }

    /**
     * Создаёт заявку владельца: выпускает одноразовый enrollment-ваучер, привязывает его к
     * профилю владельца и сохраняет заявку. Возвращает заявку и сырой код для конфига сервера.
     */
    @Transactional
    public BidResult createBid(String name, String host, String ownerUsername, Long ownerProfileId) {
        final var voucher = voucherCodeService.issue(VoucherCodeType.INVITE_MINECRAFT_SERVER, 1);
        voucherCodeService.bind(voucher.getId(), ownerProfileId);

        final var bid = create(new MinecraftServerBid()
                .setName(name)
                .setHost(host)
                .setOwnerUsername(ownerUsername)
                .setVoucherCodeId(voucher.getId())
                .setProfileId(ownerProfileId));

        return new BidResult(bid, voucher.getCode());
    }

    public Optional<MinecraftServerBid> findByHost(String host) {
        return minecraftServerBidRepository.findByHost(host);
    }

    public void delete(Long id) {
        minecraftServerBidRepository.delete(id);
    }

    public record BidResult(MinecraftServerBid bid, String code) {}
}

package com.pixplaze.api.web.repository;

import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServerBid;
import com.pixplaze.api.web.data.dto.MinecraftServerBidInfo;
import com.pixplaze.api.web.data.server.MinecraftServerBidStatus;
import com.pixplaze.api.web.util.AddressUtils;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.pixplaze.api.web.data.db.Tables.MINECRAFT_SERVER_BID;
import static com.pixplaze.api.web.data.db.Tables.PROFILE;
import static com.pixplaze.api.web.data.db.Tables.VOUCHER_CODE;

@Repository
@RequiredArgsConstructor
public class MinecraftServerBidRepository {
    private final DSLContext dslContext;

    public MinecraftServerBid create(MinecraftServerBid bid) {
        return Objects.requireNonNull(
                dslContext.insertInto(MINECRAFT_SERVER_BID)
                        .set(MINECRAFT_SERVER_BID.NAME, bid.getName())
                        .set(MINECRAFT_SERVER_BID.HOST, AddressUtils.normalizeHost(bid.getHost()))
                        .set(MINECRAFT_SERVER_BID.PORT, bid.getPort())
                        .set(MINECRAFT_SERVER_BID.OWNER_USERNAME, bid.getOwnerUsername())
                        .set(MINECRAFT_SERVER_BID.INTEGRATION, bid.getIntegration())
                        .set(MINECRAFT_SERVER_BID.VOUCHER_CODE_ID, bid.getVoucherCodeId())
                        .set(MINECRAFT_SERVER_BID.PROFILE_ID, bid.getProfileId())
                        .returning()
                        .fetchOneInto(MinecraftServerBid.class),
                "Inserted minecraft_server_bid row must be returned!"
        );
    }

    /// Открытая заявка на игровой адрес; уникальный индекс гарантирует не больше одной.
    public Optional<MinecraftServerBid> findPendingByAddress(String host, Integer port) {
        return dslContext.select()
                .from(MINECRAFT_SERVER_BID)
                .where(MINECRAFT_SERVER_BID.HOST.eq(AddressUtils.normalizeHost(host)))
                .and(MINECRAFT_SERVER_BID.PORT.eq(port))
                .and(MINECRAFT_SERVER_BID.STATUS.eq(MinecraftServerBidStatus.PENDING))
                .fetchOptionalInto(MinecraftServerBid.class);
    }

    public Optional<MinecraftServerBid> findById(Long id) {
        return dslContext.selectFrom(MINECRAFT_SERVER_BID)
                .where(MINECRAFT_SERVER_BID.ID.eq(id))
                .fetchOptionalInto(MinecraftServerBid.class);
    }

    /// Открытые заявки (опционально — только с указанной интеграцией), старые первыми.
    public List<MinecraftServerBid> findPending(MinecraftServerStateInfo.IntegrationStatus integration) {
        var condition = MINECRAFT_SERVER_BID.STATUS.eq(MinecraftServerBidStatus.PENDING);
        if (integration != null) {
            condition = condition.and(MINECRAFT_SERVER_BID.INTEGRATION.eq(integration));
        }

        return dslContext.selectFrom(MINECRAFT_SERVER_BID)
                .where(condition)
                .orderBy(MINECRAFT_SERVER_BID.CREATED_AT)
                .fetchInto(MinecraftServerBid.class);
    }

    /// Заявки для страницы заявок с кодом и именем подавшего, новые первыми. {@code null} в фильтре —
    /// без ограничения: {@code profileId} — заявки профиля, {@code status} — заявки в этом статусе.
    /// Срок жизни ({@code expiresAt}) знает сервис, здесь он не заполняется.
    public List<MinecraftServerBidInfo> findInfos(Long profileId, MinecraftServerBidStatus status) {
        var condition = DSL.noCondition();
        if (profileId != null) {
            condition = condition.and(MINECRAFT_SERVER_BID.PROFILE_ID.eq(profileId));
        }
        if (status != null) {
            condition = condition.and(MINECRAFT_SERVER_BID.STATUS.eq(status));
        }

        return dslContext.select(
                        MINECRAFT_SERVER_BID.ID,
                        MINECRAFT_SERVER_BID.NAME,
                        MINECRAFT_SERVER_BID.HOST,
                        MINECRAFT_SERVER_BID.PORT,
                        MINECRAFT_SERVER_BID.INTEGRATION,
                        MINECRAFT_SERVER_BID.STATUS,
                        VOUCHER_CODE.CODE,
                        MINECRAFT_SERVER_BID.OWNER_USERNAME,
                        PROFILE.NAME,
                        MINECRAFT_SERVER_BID.CREATED_AT)
                .from(MINECRAFT_SERVER_BID)
                .join(VOUCHER_CODE).on(VOUCHER_CODE.ID.eq(MINECRAFT_SERVER_BID.VOUCHER_CODE_ID))
                .leftJoin(PROFILE).on(PROFILE.ID.eq(MINECRAFT_SERVER_BID.PROFILE_ID))
                .where(condition)
                .orderBy(MINECRAFT_SERVER_BID.CREATED_AT.desc())
                .fetch(record -> new MinecraftServerBidInfo(
                        record.value1(), record.value2(), record.value3(), record.value4(), record.value5(),
                        record.value6(), record.value7(), record.value8(), record.value9(), record.value10(),
                        null));
    }

    /// Переводит открытые заявки, созданные раньше {@code createdBefore}, в EXPIRED; возвращает их число.
    public int expirePendingCreatedBefore(OffsetDateTime createdBefore) {
        return dslContext.update(MINECRAFT_SERVER_BID)
                .set(MINECRAFT_SERVER_BID.STATUS, MinecraftServerBidStatus.EXPIRED)
                .where(MINECRAFT_SERVER_BID.STATUS.eq(MinecraftServerBidStatus.PENDING))
                .and(MINECRAFT_SERVER_BID.CREATED_AT.lt(createdBefore))
                .execute();
    }

    /// Закрывает заявку (APPROVED/REJECTED): запись остаётся для истории, адрес освобождается.
    /// Закрывает только открытую заявку; {@code false} — заявка уже закрыта (гонка проверки MOTD и админа).
    public boolean setStatus(Long id, MinecraftServerBidStatus status) {
        return dslContext.update(MINECRAFT_SERVER_BID)
                .set(MINECRAFT_SERVER_BID.STATUS, status)
                .where(MINECRAFT_SERVER_BID.ID.eq(id))
                .and(MINECRAFT_SERVER_BID.STATUS.eq(MinecraftServerBidStatus.PENDING))
                .execute() > 0;
    }
}

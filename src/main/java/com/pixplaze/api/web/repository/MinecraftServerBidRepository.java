package com.pixplaze.api.web.repository;

import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServerBid;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

import static com.pixplaze.api.web.data.db.Tables.MINECRAFT_SERVER_BID;

@Repository
@RequiredArgsConstructor
public class MinecraftServerBidRepository {
    private final DSLContext dslContext;

    public MinecraftServerBid create(MinecraftServerBid bid) {
        return Objects.requireNonNull(
                dslContext.insertInto(MINECRAFT_SERVER_BID)
                        .set(MINECRAFT_SERVER_BID.NAME, bid.getName())
                        .set(MINECRAFT_SERVER_BID.HOST, bid.getHost())
                        .set(MINECRAFT_SERVER_BID.OWNER_USERNAME, bid.getOwnerUsername())
                        .set(MINECRAFT_SERVER_BID.VOUCHER_CODE_ID, bid.getVoucherCodeId())
                        .set(MINECRAFT_SERVER_BID.PROFILE_ID, bid.getProfileId())
                        .returning()
                        .fetchOneInto(MinecraftServerBid.class),
                "Inserted minecraft_server_bid row must be returned!"
        );
    }

    public Optional<MinecraftServerBid> findByHost(String host) {
        return dslContext.select()
                .from(MINECRAFT_SERVER_BID)
                .where(MINECRAFT_SERVER_BID.HOST.eq(host))
                .fetchOptionalInto(MinecraftServerBid.class);
    }

    public void delete(Long id) {
        dslContext.delete(MINECRAFT_SERVER_BID)
                .where(MINECRAFT_SERVER_BID.ID.eq(id))
                .execute();
    }
}

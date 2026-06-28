package com.pixplaze.api.web.repository;

import com.pixplaze.api.web.data.db.tables.pojos.Profile;
import com.pixplaze.api.web.data.db.tables.pojos.VoucherCode;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.web.exception.voucher.VoucherCodeValidationException;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.pixplaze.api.web.data.db.Tables.VOUCHER_CODE;
import static com.pixplaze.api.web.data.db.Tables.VOUCHER_CODE_PROFILE;
import static com.pixplaze.api.web.data.db.tables.ProfileTable.PROFILE;

@RequiredArgsConstructor
@Repository
public class VoucherCodeRepository {
    private final DSLContext dslContext;

    public Optional<VoucherCode> getByCode(String code) {
        return dslContext.select()
                .from(VOUCHER_CODE)
                .where(VOUCHER_CODE.CODE.eq(code))
                .fetchOptionalInto(VoucherCode.class);
    }

    public void create(VoucherCode voucherCode) {
        dslContext.insertInto(VOUCHER_CODE)
                .set(VOUCHER_CODE.CODE, voucherCode.getCode())
                .set(VOUCHER_CODE.DESCRIPTION, voucherCode.getDescription())
                .set(VOUCHER_CODE.TYPE, voucherCode.getType())
                .set(VOUCHER_CODE.EXPIRES_AT, voucherCode.getExpiresAt())
                .set(VOUCHER_CODE.ACTIVATIONS_LIMIT, voucherCode.getActivationsLimit())
                .set(VOUCHER_CODE.ACTIVATIONS_REMAINING, voucherCode.getActivationsLimit())
                .execute();
    }

    public void update(VoucherCode voucherCode) {
        final var activationsRemainingExpression = VOUCHER_CODE.ACTIVATIONS_REMAINING.plus(
                DSL.value(voucherCode.getActivationsRemaining()).minus(VOUCHER_CODE.ACTIVATIONS_LIMIT)
        );
        dslContext.update(VOUCHER_CODE)
                .set(VOUCHER_CODE.CODE, voucherCode.getCode())
                .set(VOUCHER_CODE.DESCRIPTION, voucherCode.getDescription())
                .set(VOUCHER_CODE.TYPE, voucherCode.getType())
                .set(VOUCHER_CODE.EXPIRES_AT, voucherCode.getExpiresAt())
                .set(VOUCHER_CODE.ACTIVATIONS_LIMIT, voucherCode.getActivationsLimit())
                .set(VOUCHER_CODE.ACTIVATIONS_REMAINING, activationsRemainingExpression)
                .where(VOUCHER_CODE.ID.eq(voucherCode.getId()))
                .execute();
    }

    @Transactional
    public void activate(VoucherCode voucherCode, Long profileId) {
        final var voucherCodesRowsAffected = dslContext.update(VOUCHER_CODE)
                .set(VOUCHER_CODE.ACTIVATIONS_REMAINING, VOUCHER_CODE.ACTIVATIONS_REMAINING.minus(1))
                .where(VOUCHER_CODE.ID.equal(voucherCode.getId()))
                .and(VOUCHER_CODE.ACTIVATIONS_REMAINING.greaterThan(0).or(VOUCHER_CODE.ACTIVATIONS_LIMIT.eq(-1)))
                .and(VOUCHER_CODE.EXPIRES_AT.isNull().or(VOUCHER_CODE.EXPIRES_AT.greaterOrEqual(DSL.currentOffsetDateTime())))
                .execute();

        if (voucherCodesRowsAffected == 0) {
            throw new VoucherCodeValidationException("Voucher code '%s' exhausted and can not be activated!".formatted(voucherCode.getCode()));
        }

        // Привязка могла быть создана раньше (в заявке) со значением activated_at = NULL —
        // тогда активируем её. Если строки нет, создаём сразу активированной. Повторная
        // активация уже активированной (activated_at IS NOT NULL) строки даёт 0 и отвергается.
        final var voucherCodesActivationsRowsAffected = dslContext.insertInto(VOUCHER_CODE_PROFILE)
                .set(VOUCHER_CODE_PROFILE.VOUCHER_CODE_ID, voucherCode.getId())
                .set(VOUCHER_CODE_PROFILE.PROFILE_ID, profileId)
                .set(VOUCHER_CODE_PROFILE.ACTIVATED_AT, DSL.currentOffsetDateTime())
                .onConflict(VOUCHER_CODE_PROFILE.PROFILE_ID, VOUCHER_CODE_PROFILE.VOUCHER_CODE_ID)
                .doUpdate()
                .set(VOUCHER_CODE_PROFILE.ACTIVATED_AT, DSL.currentOffsetDateTime())
                .where(VOUCHER_CODE_PROFILE.ACTIVATED_AT.isNull())
                .execute();

        if (voucherCodesActivationsRowsAffected == 0) {
            throw new VoucherCodeValidationException("Voucher code has already been activated by profile id '%s'!".formatted(profileId));
        }
    }

    @Transactional
    public void activate(VoucherCode voucherCode, Profile profile) {
        activate(voucherCode, profile.getId());
    }

    /// Привязывает код к профилю без активации (заявка): activated_at остаётся NULL.
    /// Идемпотентна — повторная привязка той же пары игнорируется.
    public void bind(Long voucherCodeId, Long profileId) {
        dslContext.insertInto(VOUCHER_CODE_PROFILE)
                .set(VOUCHER_CODE_PROFILE.VOUCHER_CODE_ID, Objects.requireNonNull(voucherCodeId, "Voucher code `id` must not be null!"))
                .set(VOUCHER_CODE_PROFILE.PROFILE_ID, Objects.requireNonNull(profileId, "Profile `id` must not be null!"))
                .onConflictDoNothing()
                .execute();
    }

    /// Привязан ли код к профилю (выдан ему в заявке), независимо от факта активации.
    public boolean isBoundTo(Long voucherCodeId, Long profileId) {
        return dslContext.fetchExists(
                VOUCHER_CODE_PROFILE,
                VOUCHER_CODE_PROFILE.VOUCHER_CODE_ID.eq(voucherCodeId)
                        .and(VOUCHER_CODE_PROFILE.PROFILE_ID.eq(profileId))
        );
    }

    public void delete(Long id) {
        dslContext.delete(VOUCHER_CODE)
                .where(VOUCHER_CODE.ID.eq(id))
                .execute();
    }

    public void addVoucherActivation(VoucherCode voucherCode, ApplicationClientPrincipal clientPrincipial) {
        dslContext.insertInto(VOUCHER_CODE_PROFILE)
                .set(VOUCHER_CODE_PROFILE.VOUCHER_CODE_ID, Objects.requireNonNull(voucherCode.getId(), "Voucher code `id` field must not be null!"))
                .set(VOUCHER_CODE_PROFILE.PROFILE_ID, Objects.requireNonNull(clientPrincipial.getId(), "User `id` field must not be null!"))
                .set(VOUCHER_CODE_PROFILE.ACTIVATED_AT, DSL.currentOffsetDateTime())
                .execute();
    }

    public List<Map<String, Object>> getVoucherCodesActivations() {
        return dslContext.select()
                .from(VOUCHER_CODE_PROFILE)
                .join(VOUCHER_CODE).on(VOUCHER_CODE_PROFILE.VOUCHER_CODE_ID.eq(VOUCHER_CODE.ID))
                .join(PROFILE).on(VOUCHER_CODE_PROFILE.PROFILE_ID.eq(PROFILE.ID))
                .fetchMaps();
    }
}

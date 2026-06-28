package com.pixplaze.api.web.service;

import com.pixplaze.api.web.data.db.tables.pojos.Profile;
import com.pixplaze.api.web.data.db.tables.pojos.VoucherCode;
import com.pixplaze.api.web.data.voucher.VoucherCodeType;
import com.pixplaze.api.web.exception.voucher.VoucherCodeValidationException;
import com.pixplaze.api.web.repository.VoucherCodeRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@Service
public class VoucherCodeService {
    private static final char[] CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final int CODE_LENGTH = 8;

    private final SecureRandom secureRandom = new SecureRandom();
    private final VoucherCodeRepository voucherCodeRepository;

    public Optional<VoucherCode> getByCode(String code) {
        return voucherCodeRepository.getByCode(code);
    }

    /// Выпускает новый ваучер с уникальным сгенерированным кодом и возвращает его (с проставленным id).
    public VoucherCode issue(VoucherCodeType type, int activationsLimit) {
        final var code = generateUniqueCode();
        voucherCodeRepository.create(new VoucherCode()
                .setCode(code)
                .setType(type)
                .setActivationsLimit(activationsLimit));
        return getByCode(code)
                .orElseThrow(() -> new IllegalStateException("Just created voucher code not found: " + code));
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = generateCode();
        } while (getByCode(code).isPresent());
        return code;
    }

    private String generateCode() {
        final var result = new char[CODE_LENGTH];
        for (int i = 0; i < CODE_LENGTH; i++) {
            result[i] = CODE_ALPHABET[secureRandom.nextInt(CODE_ALPHABET.length)];
        }
        return new String(result);
    }

    public void create(VoucherCode voucherCode) {
        voucherCodeRepository.create(voucherCode);
    }

    public void update(VoucherCode voucherCode) {
        voucherCodeRepository.update(voucherCode);
    }

    public void delete(VoucherCode voucherCode) {
        voucherCodeRepository.delete(voucherCode.getId());
    }

    public void activate(VoucherCode voucherCode, Profile profile) {
        voucherCodeRepository.activate(voucherCode, profile);
    }

    public void activate(VoucherCode voucherCode, Long profileId) {
        voucherCodeRepository.activate(voucherCode, profileId);
    }

    /// Привязывает код к профилю без активации (заявка).
    public void bind(Long voucherCodeId, Long profileId) {
        voucherCodeRepository.bind(voucherCodeId, profileId);
    }

    /// Привязан ли код к профилю (выдан ему в заявке).
    public boolean isBoundTo(Long voucherCodeId, Long profileId) {
        return voucherCodeRepository.isBoundTo(voucherCodeId, profileId);
    }

    /// Validates and loads voucher code DTO
    /// @return {@link VoucherCode} if validation successful,
    /// otherwise throws {@link VoucherCodeValidationException}
    public @NonNull VoucherCode load(String code) throws VoucherCodeValidationException {
        final var voucherCode = getByCode(validate(code))
                .orElseThrow(onVoucherCodeIsNotExistException(code));
        return validate(voucherCode, voucherCode.getType());
    }

    public @NonNull VoucherCode load(String code, VoucherCodeType type) throws VoucherCodeValidationException {
        final var voucherCode = getByCode(validate(code))
                .orElseThrow(onVoucherCodeIsNotExistException(code));
        return validate(voucherCode, type);
    }

    public String validate(String code) throws VoucherCodeValidationException {
        if (Objects.isNull(code)) {
            throw new VoucherCodeValidationException("Voucher code must not be null!");
        }

        if (!isVoucherCodeMatchPattern(code)) {
            throw new VoucherCodeValidationException("Voucher code '%s' does not match pattern!".formatted(code));
        }

        return code;
    }

    /// Validates provided {@code voucherCode}
    /// @return same {@link VoucherCode} object if voucher code is valid
    /// @throws VoucherCodeValidationException if voucher cod is invalid
    public VoucherCode validate(VoucherCode voucherCode) throws VoucherCodeValidationException {
        return validate(voucherCode, null);
    }

    /// Validates provided {@code voucherCode} with specific {@link VoucherCodeType} type
    /// @return same {@link VoucherCode} object if voucher code is valid and {@code type} matches
    /// @throws VoucherCodeValidationException if voucher code is invalid or {@code type} not matches
    public VoucherCode validate(VoucherCode voucherCode, VoucherCodeType type) {
        if (!isVoucherCodeNotNull(voucherCode)) {
            throw new VoucherCodeValidationException("Voucher code must not be null!");
        }

        if (!isVoucherCodeNotExpired(voucherCode)) {
            throw new VoucherCodeValidationException("Voucher code '%s' is expired!".formatted(voucherCode.getCode()));
        }

        if (!isVoucherCodeActivationsRemaining(voucherCode)) {
            throw new VoucherCodeValidationException("Voucher code '%s' exhausted and can not been activated!".formatted(voucherCode.getCode()));
        }

        if (!isVoucherCodeMatchType(voucherCode, type)) {
            throw new VoucherCodeValidationException("Voucher code '%s' is not acceptable as voucher of type '%s'!".formatted(voucherCode.getCode(), VoucherCodeType.INVITE));
        }

        return voucherCode;
    }

    private boolean isVoucherCodeNotNull(VoucherCode voucherCode) {
        return Objects.nonNull(voucherCode);
    }

    private boolean isVoucherCodeMatchPattern(String code) {
        final var pattern = Pattern.compile("[A-Z0-9]{8}");
        return pattern.matcher(code).matches();
    }

    private boolean isVoucherCodeActivationsRemaining(VoucherCode voucherCode) {
        return voucherCode.getActivationsLimit() == -1 || voucherCode.getActivationsRemaining() > 0;
    }

    public boolean isVoucherCodeNotExpired(VoucherCode voucherCode) {
        return voucherCode.getExpiresAt() == null || voucherCode.getExpiresAt().isAfter(OffsetDateTime.now());
    }

    private boolean isVoucherCodeMatchType(VoucherCode voucherCode, VoucherCodeType type) {
        return type == null || type.equals(voucherCode.getType());
    }

    private Supplier<VoucherCodeValidationException> onVoucherCodeIsNotExistException(String code) {
        return () -> new VoucherCodeValidationException("Voucher code '%s' is not exist!".formatted(code));
    }
}

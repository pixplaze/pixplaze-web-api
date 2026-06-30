package com.pixplaze.api.web.util;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public class NullUtils {

    public static boolean isZeroOrNull(@Nullable Long number) {
        return Objects.isNull(number) || number == 0;
    }

    public static boolean isZeroOrNull(@Nullable Integer number) {
        return Objects.isNull(number) || number == 0;
    }

    public static boolean nonZeroOrNull(@Nullable Long number) {
        return !isZeroOrNull(number);
    }

    public static boolean nonZeroOrNull(@Nullable Integer number) {
        return !isZeroOrNull(number);
    }

    public static boolean isNullOrEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    public static boolean isNullOrEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    public static <T> T nullOf(Class<T> typeClass) {
        return typeClass.cast(null);
    }

    public static <T> T requireNonNull(T value, String messagePattern, Object... messageParameters) {
        return requireNonNull(value, () -> String.format(messagePattern, messageParameters));
    }

    public static <T> T requireNonNull(T value, Supplier<String> messageSupplier) {
        if (value == null) {
            throw new NullPointerException(messageSupplier.get());
        }

        return value;
    }

    public static <T> void ifPresentConsume(T value, Consumer<T> onPresentAction) {
        if (value == null) {
            return;
        }

        onPresentAction.accept(value);
    }

    public static <T, R> R ifPresentApply(T value, R onNullValue, Function<T, R> onPresentAction) {
        if (value == null) {
            return onNullValue;
        }

        return onPresentAction.apply(value);
    }

    public static <T, R> R ifPresentApply(T value, Function<T, R> onPresentAction) {
        return ifPresentApply(value, null, onPresentAction);
    }

    public static <T> void ifPresentOrElse(T value, Consumer<T> onPresentAction, Runnable onNullAction) {
        if (value == null) {
            onNullAction.run();
            return;
        }

        onPresentAction.accept(value);
    }

    public static <T> T getOrDefault(T value, T defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        return value;
    }

    /**
     Returns 1L if a {@code number} is null or 0.

     @param number value being checked
     @return not null
     */
    @Nonnull
    private static Long oneOnEmpty(Long number) {
        return Objects.isNull(number) || number == 0 ? 1 : number;
    }

    /**
     Returns 1 if a {@code number} is null or 0.

     @param number value being checked
     @return not null
     */
    @Nonnull
    public static Integer oneOnEmpty(Integer number) {
        return Objects.isNull(number) || number == 0 ? 1 : number;
    }

    public static Double zeroOnEmpty(Double number) {
        return number == null ? 0 : number;
    }
}
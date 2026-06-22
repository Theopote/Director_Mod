package com.director.util;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * 解析工具：将持久化/配置中的松散字符串安全转换为强类型值。
 * <p>
 * 仅捕获预期的解析异常（{@link IllegalArgumentException}、{@link NumberFormatException}），
 * 失败时记录 debug 日志并返回默认值，不吞编程错误。
 */
public final class ParseUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParseUtils.class);

    private ParseUtils() {
    }

    @Nullable
    public static <E extends Enum<E>> E enumOrNull(@Nullable String raw, Class<E> type, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException e) {
            LOGGER.debug("[Parse] Invalid enum {} for {}: '{}'", type.getSimpleName(), field, raw);
            return null;
        }
    }

    public static <E extends Enum<E>> E enumOr(@Nullable String raw, Class<E> type, E defaultValue, String field) {
        E parsed = enumOrNull(raw, type, field);
        return parsed != null ? parsed : defaultValue;
    }

    public static <E extends Enum<E>> void setEnumIfValid(
            @Nullable String raw,
            Class<E> type,
            String field,
            Consumer<E> setter) {
        E parsed = enumOrNull(raw, type, field);
        if (parsed != null) {
            setter.accept(parsed);
        }
    }

    public static <E extends Enum<E>> E enumFromObject(
            @Nullable Object value,
            Class<E> type,
            E defaultValue,
            String field) {
        if (value instanceof String s) {
            return enumOr(s, type, defaultValue, field);
        }
        return defaultValue;
    }

    @Nullable
    public static UUID uuidOrNull(@Nullable String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            LOGGER.debug("[Parse] Invalid UUID for {}: '{}'", field, raw);
            return null;
        }
    }

    public static void setUuidIfValid(@Nullable String raw, String field, Consumer<UUID> setter) {
        UUID uuid = uuidOrNull(raw, field);
        if (uuid != null) {
            setter.accept(uuid);
        }
    }

    public static float asFloat(@Nullable Object value, float defaultValue, String field) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value instanceof String s) {
            try {
                return Float.parseFloat(s);
            } catch (NumberFormatException e) {
                LOGGER.debug("[Parse] Invalid float for {}: '{}'", field, s);
            }
        }
        return defaultValue;
    }

    public static int asInt(@Nullable Object value, int defaultValue, String field) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                LOGGER.debug("[Parse] Invalid int for {}: '{}'", field, s);
            }
        }
        return defaultValue;
    }

    public static boolean asBoolean(@Nullable Object value, boolean defaultValue) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return defaultValue;
    }
}

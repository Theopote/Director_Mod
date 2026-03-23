package com.director.core.animation.effect.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 特效 UI 工具类
 * 
 * <p>提供通用的参数读取和 UI 渲染辅助方法，减少每个特效写 UI 的重复代码。
 * 
 * @author Director Team
 * @version 1.0
 */
public final class EffectUIUtils {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(EffectUIUtils.class);
    
    private EffectUIUtils() {}
    
    /**
     * 安全获取浮点参数
     * 
     * @param params 参数 Map
     * @param key 参数键
     * @param def 默认值
     * @return 参数值或默认值
     */
    public static float getFloat(Map<String, Object> params, String key, float def) {
        if (params == null) return def;
        Object v = params.get(key);
        switch (v) {
            case null -> {
                return def;
            }
            case Number n -> {
                return n.floatValue();
            }
            case String s -> {
                try {
                    return Float.parseFloat(s);
                } catch (NumberFormatException e) {
                    LOGGER.warn("Effect param {} parse float failed: {}", key, s);
                    return def;
                }
            }
            default -> {
            }
        }

        LOGGER.warn("Effect param {} type mismatch, expected Number or String, got: {}",
                   key, v.getClass().getSimpleName());
        return def;
    }
    
    /**
     * 安全获取布尔参数
     * 
     * @param params 参数 Map
     * @param key 参数键
     * @param def 默认值
     * @return 参数值或默认值
     */
    public static boolean getBoolean(Map<String, Object> params, String key, boolean def) {
        if (params == null) return def;
        Object v = params.get(key);
        switch (v) {
            case null -> {
                return def;
            }
            case Boolean b -> {
                return b;
            }
            case String s -> {
                return Boolean.parseBoolean(s);
            }
            default -> {
            }
        }

        LOGGER.warn("Effect param {} type mismatch, expected Boolean or String, got: {}",
                   key, v.getClass().getSimpleName());
        return def;
    }
    
    /**
     * 安全获取整数参数
     * 
     * @param params 参数 Map
     * @param key 参数键
     * @param def 默认值
     * @return 参数值或默认值
     */
    public static int getInt(Map<String, Object> params, String key, int def) {
        if (params == null) return def;
        Object v = params.get(key);
        switch (v) {
            case null -> {
                return def;
            }
            case Number n -> {
                return n.intValue();
            }
            case String s -> {
                try {
                    return Integer.parseInt(s);
                } catch (NumberFormatException e) {
                    LOGGER.warn("Effect param {} parse int failed: {}", key, s);
                    return def;
                }
            }
            default -> {
            }
        }

        LOGGER.warn("Effect param {} type mismatch, expected Number or String, got: {}",
                   key, v.getClass().getSimpleName());
        return def;
    }
    
    /**
     * 安全获取字符串参数
     * 
     * @param params 参数 Map
     * @param key 参数键
     * @param def 默认值
     * @return 参数值或默认值
     */
    public static String getString(Map<String, Object> params, String key, String def) {
        if (params == null) return def;
        Object v = params.get(key);
        return v == null ? def : v.toString();
    }
}


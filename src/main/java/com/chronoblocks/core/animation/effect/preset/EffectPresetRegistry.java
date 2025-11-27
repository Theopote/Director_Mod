package com.chronoblocks.core.animation.effect.preset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 特效预设注册中心
 * 
 * <p>管理所有已注册的特效预设包，提供统一的访问接口。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class EffectPresetRegistry {
    
    private static final List<EffectPreset> PRESETS = new ArrayList<>();
    
    static {
        // 注册内建预设
        register(new MagicSummonPreset());
        register(new SciFiAssemblePreset());
        register(new EarthRisePreset());
        register(new ConstructionDustPreset());
    }
    
    /**
     * 注册一个新的特效预设
     * 
     * @param preset 预设实例
     */
    public static void register(EffectPreset preset) {
        if (preset != null) {
            PRESETS.add(preset);
        }
    }
    
    /**
     * 获取所有已注册的预设列表
     * 
     * @return 不可修改的预设列表
     */
    public static List<EffectPreset> getPresets() {
        return Collections.unmodifiableList(PRESETS);
    }
    
    /**
     * 根据标签过滤预设
     * 
     * @param tag 标签名称
     * @return 包含指定标签的预设列表
     */
    public static List<EffectPreset> getPresetsByTag(String tag) {
        if (tag == null || tag.isEmpty()) {
            return getPresets();
        }
        return PRESETS.stream()
                .filter(preset -> preset.getTags().contains(tag))
                .toList();
    }
    
    /**
     * 清空所有预设（主要用于测试）
     */
    public static void clear() {
        PRESETS.clear();
    }
}


package com.chronoblocks.core.animation.effect;

import com.chronoblocks.core.animation.effect.impl.DustBurstEffect;
import com.chronoblocks.core.animation.effect.impl.FlashWhiteEffect;
import com.chronoblocks.core.animation.effect.impl.GlowPulseEffect;
import com.chronoblocks.core.animation.effect.impl.MagicFlareEffect;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * 简单的 Effect 注册中心。
 * 
 * <p>后续如果有数据驱动/插件化可以再往上抽象。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class BlockEffectRegistry {
    
    private static final Map<EffectType, IBlockEffect> EFFECTS =
            new EnumMap<>(EffectType.class);
    
    static {
        // 在这里注册内建特效
        register(EffectType.FLASH_WHITE, new FlashWhiteEffect());
        register(EffectType.DUST_BURST, new DustBurstEffect());
        register(EffectType.MAGIC_FLARE, new MagicFlareEffect());
        register(EffectType.GLOW_PULSE, new GlowPulseEffect());
    }
    
    private BlockEffectRegistry() {}
    
    public static void register(EffectType type, IBlockEffect effect) {
        EFFECTS.put(type, effect);
    }
    
    @Nullable
    public static IBlockEffect get(EffectType type) {
        return EFFECTS.get(type);
    }
    
    /**
     * 获取特效实现，如果不存在则返回空对象
     * 
     * <p>用于防止 UI 渲染时空指针崩溃
     * 
     * @param type 特效类型
     * @return 特效实现，如果不存在则返回空对象
     */
    public static IBlockEffect getOrEmpty(EffectType type) {
        IBlockEffect effect = EFFECTS.get(type);
        return effect != null ? effect : com.chronoblocks.core.animation.effect.impl.NullBlockEffect.getInstance();
    }
    
    /**
     * 获取所有已注册的特效类型
     * 
     * @return 已注册的特效类型列表
     */
    public static java.util.List<EffectType> getRegisteredTypes() {
        return EFFECTS.keySet().stream()
                .sorted(java.util.Comparator.comparing(Enum::ordinal))
                .toList();
    }
}


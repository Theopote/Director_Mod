package com.chronoblocks.core.animation.transform;

import com.chronoblocks.core.container.AnimationMode;
import com.chronoblocks.core.animation.transform.impl.*;

import java.util.Map;

/**
 * 动画变换器工厂
 * 
 * <p>根据 AnimationMode 创建对应的动画变换器实例。
 * 使用单例模式和缓存机制。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class AnimationTransformerFactory {
    
    private static final AnimationTransformerFactory INSTANCE = new AnimationTransformerFactory();
    
    // 变换器注册表：每个模式对应一个变换器实例（使用 EnumMap 提升性能）
    private final Map<AnimationMode, IAnimationTransformer> registry = new java.util.EnumMap<>(AnimationMode.class);
    
    private AnimationTransformerFactory() {
        registerTransformers();
    }
    
    /**
     * 获取单例实例
     */
    public static AnimationTransformerFactory getInstance() {
        return INSTANCE;
    }
    
    /**
     * 注册所有动画变换器
     */
    private void registerTransformers() {
        // 注册所有变换器
        register(AnimationMode.INSTANT, new InstantTransformer());
        register(AnimationMode.FADE_IN, new FadeInTransformer());
        register(AnimationMode.FADE_OUT, new FadeOutTransformer());
        register(AnimationMode.GROW_UP, new GrowUpTransformer());
        register(AnimationMode.EXPAND, new ExpandTransformer());
        register(AnimationMode.EXPLODE, new ExplodeTransformer());
        register(AnimationMode.IMPLODE, new ImplodeTransformer());
        register(AnimationMode.ASSEMBLE, new AssembleTransformer());
        register(AnimationMode.FALL_DOWN, new FallDownTransformer());
        register(AnimationMode.RISE_UP, new RiseUpTransformer());
        register(AnimationMode.FLY_IN, new FlyInTransformer());
        register(AnimationMode.SHAKER, new ShakerTransformer());
    }
    
    /**
     * 注册动画变换器
     */
    private void register(AnimationMode mode, IAnimationTransformer transformer) {
        if (mode != null && transformer != null) {
            registry.put(mode, transformer);
        }
    }
    
    /**
     * 根据动画模式获取对应的变换器实例
     * 
     * @param mode 动画模式
     * @return 动画变换器实例，如果模式不支持则返回默认变换器（INSTANT）
     */
    public IAnimationTransformer getTransformer(AnimationMode mode) {
        if (mode == null) {
            mode = AnimationMode.INSTANT;
        }
        return registry.getOrDefault(mode, registry.get(AnimationMode.INSTANT));
    }
}


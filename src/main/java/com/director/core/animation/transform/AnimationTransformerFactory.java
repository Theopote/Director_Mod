package com.director.core.animation.transform;

import com.director.core.container.AnimationMode;
import com.director.core.animation.transform.impl.*;

import java.util.Map;

/**
 * 动画变换器工厂
 * 
 * <p>根据 AnimationMode 创建对应的动画变换器实例。
 * 使用单例模式和缓存机制。
 * 
 * @author Director Team
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
        // 新增动画模式
        register(AnimationMode.POP_IN, new PopInTransformer());
        register(AnimationMode.SHRINK_OUT, new ShrinkOutTransformer());
        register(AnimationMode.GRAVITY_FALL, new GravityFallTransformer());
        register(AnimationMode.FLY_OUT, new FlyOutTransformer());

        // ✅ 旧版排序策略模式：映射到 InstantTransformer
        // 这些模式的"排序策略"已由 LegacyAnimationModeMigrator 迁移到 SequenceParameters，
        // 此处仅提供 Transformer 回退，避免未迁移的旧存档静默使用无效变换。
        IAnimationTransformer instant = registry.get(AnimationMode.INSTANT);
        register(AnimationMode.GROW_DOWN, instant);
        register(AnimationMode.SLICE, instant);
        register(AnimationMode.RANDOM, instant);
        register(AnimationMode.WAVE, instant);
        register(AnimationMode.SPIRAL, instant);
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
        IAnimationTransformer transformer = registry.get(mode);
        if (transformer == null) {
            org.slf4j.LoggerFactory.getLogger(AnimationTransformerFactory.class)
                    .warn("[AnimationTransformerFactory] 未注册的动画模式 {}，回退到 INSTANT", mode);
            transformer = registry.get(AnimationMode.INSTANT);
        }
        return transformer;
    }
}


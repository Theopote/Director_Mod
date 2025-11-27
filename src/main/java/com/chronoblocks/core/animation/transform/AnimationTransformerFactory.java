package com.chronoblocks.core.animation.transform;

import com.chronoblocks.core.container.AnimationMode;
import com.chronoblocks.core.animation.transform.impl.*;

import java.util.HashMap;
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
    
    // 变换器缓存：每个模式对应一个变换器实例
    private final Map<AnimationMode, IAnimationTransformer> transformerCache = new HashMap<>();
    
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
        // 注册基础变换器
        registerTransformer(AnimationMode.INSTANT, new InstantTransformer());
        registerTransformer(AnimationMode.FADE_IN, new FadeInTransformer());
        registerTransformer(AnimationMode.GROW_UP, new GrowUpTransformer());
        registerTransformer(AnimationMode.FALL_DOWN, new FallDownTransformer());
        
        // 注册新增的变换器
        registerTransformer(AnimationMode.EXPAND, new ExpandTransformer());
        registerTransformer(AnimationMode.RISE_UP, new RiseUpTransformer());
        registerTransformer(AnimationMode.FLY_IN, new FlyInTransformer());
        registerTransformer(AnimationMode.SHAKER, new ShakerTransformer());
        
        // TODO: 后续添加更多变换器
        // registerTransformer(AnimationMode.ASSEMBLE, new AssembleTransformer());
        
        // 对于未实现的模式，使用 InstantTransformer 作为后备
        for (AnimationMode mode : AnimationMode.values()) {
            if (!transformerCache.containsKey(mode)) {
                transformerCache.put(mode, new InstantTransformer());
            }
        }
    }
    
    /**
     * 注册动画变换器
     */
    private void registerTransformer(AnimationMode mode, IAnimationTransformer transformer) {
        if (mode != null && transformer != null) {
            transformerCache.put(mode, transformer);
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
        
        IAnimationTransformer transformer = transformerCache.get(mode);
        if (transformer == null) {
            // 如果找不到对应的变换器，返回默认变换器
            transformer = transformerCache.get(AnimationMode.INSTANT);
        }
        
        return transformer;
    }
    
    /**
     * 检查指定的动画模式是否有对应的变换器实现
     * 
     * @param mode 动画模式
     * @return 如果有变换器返回 true，否则返回 false
     */
    public boolean hasTransformer(AnimationMode mode) {
        return mode != null && transformerCache.containsKey(mode) 
            && !(transformerCache.get(mode) instanceof InstantTransformer);
    }
}


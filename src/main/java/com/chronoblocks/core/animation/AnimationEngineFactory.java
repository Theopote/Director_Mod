package com.chronoblocks.core.animation;

import com.chronoblocks.core.container.AnimationMode;
import com.chronoblocks.core.animation.impl.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 动画引擎工厂
 * 
 * <p>根据 AnimationMode 创建对应的动画引擎实例
 * 
 * <p>使用单例模式和缓存机制，确保每个动画模式只有一个引擎实例
 */
public class AnimationEngineFactory {
    private static final AnimationEngineFactory INSTANCE = new AnimationEngineFactory();
    
    // 引擎缓存：每个模式对应一个引擎实例
    private final Map<AnimationMode, BlockAnimationEngine> engineCache = new HashMap<>();
    
    private AnimationEngineFactory() {
        // 注册所有动画引擎
        registerEngines();
    }
    
    /**
     * 获取单例实例
     */
    public static AnimationEngineFactory getInstance() {
        return INSTANCE;
    }
    
    /**
     * 注册所有动画引擎
     */
    private void registerEngines() {
        // 注册基础引擎
        registerEngine(AnimationMode.INSTANT, new InstantAnimationEngine());
        registerEngine(AnimationMode.GROW_UP, new GrowUpAnimationEngine());
        registerEngine(AnimationMode.EXPAND, new ExpandAnimationEngine());
        
        // TODO: 后续添加更多动画引擎
        // registerEngine(AnimationMode.GROW_DOWN, new GrowDownAnimationEngine());
        // registerEngine(AnimationMode.FADE_IN, new FadeInAnimationEngine());
        // ...
    }
    
    /**
     * 注册动画引擎
     */
    private void registerEngine(AnimationMode mode, BlockAnimationEngine engine) {
        if (mode != null && engine != null) {
            engineCache.put(mode, engine);
        }
    }
    
    /**
     * 根据动画模式获取对应的引擎实例
     * 
     * @param mode 动画模式
     * @return 动画引擎实例，如果模式不支持则返回默认引擎（INSTANT）
     */
    public BlockAnimationEngine getEngine(AnimationMode mode) {
        if (mode == null) {
            mode = AnimationMode.INSTANT;
        }
        
        BlockAnimationEngine engine = engineCache.get(mode);
        if (engine == null) {
            // 如果找不到对应的引擎，返回默认引擎
            engine = engineCache.get(AnimationMode.INSTANT);
        }
        
        return engine;
    }
    
    /**
     * 检查指定的动画模式是否有对应的引擎实现
     * 
     * @param mode 动画模式
     * @return 如果有引擎返回 true，否则返回 false
     */
    public boolean hasEngine(AnimationMode mode) {
        return mode != null && engineCache.containsKey(mode);
    }
}


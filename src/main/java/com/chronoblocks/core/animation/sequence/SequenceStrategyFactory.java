package com.chronoblocks.core.animation.sequence;

import com.chronoblocks.core.animation.sequence.impl.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 序列策略工厂
 * 
 * <p>根据 SequenceMode 创建对应的序列策略实例。
 * 使用单例模式和缓存机制，确保每个模式只有一个策略实例。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class SequenceStrategyFactory {
    private static final SequenceStrategyFactory INSTANCE = new SequenceStrategyFactory();
    
    // 策略缓存：每个模式对应一个策略实例
    private final Map<SequenceMode, SequenceStrategy> strategyCache = new HashMap<>();
    
    private SequenceStrategyFactory() {
        // 注册所有序列策略
        registerStrategies();
    }
    
    /**
     * 获取单例实例
     */
    public static SequenceStrategyFactory getInstance() {
        return INSTANCE;
    }
    
    /**
     * 注册所有序列策略
     */
    private void registerStrategies() {
        // 注册基础策略
        registerStrategy(SequenceMode.ONE_BY_ONE, new OneByOneStrategy());
        registerStrategy(SequenceMode.RANDOM, new RandomStrategy());
        registerStrategy(SequenceMode.BATCH, new BatchStrategy());
        
        // 注册高级策略
        registerStrategy(SequenceMode.WAVE, new WaveStrategy());
        registerStrategy(SequenceMode.SPIRAL, new SpiralStrategy());
        registerStrategy(SequenceMode.PARALLEL, new ParallelStrategy());
    }
    
    /**
     * 注册序列策略
     */
    private void registerStrategy(SequenceMode mode, SequenceStrategy strategy) {
        if (mode != null && strategy != null) {
            strategyCache.put(mode, strategy);
        }
    }
    
    /**
     * 根据序列模式获取对应的策略实例
     * 
     * @param mode 序列模式
     * @return 序列策略实例，如果模式不支持则返回默认策略（ONE_BY_ONE）
     */
    public SequenceStrategy getStrategy(SequenceMode mode) {
        if (mode == null) {
            mode = SequenceMode.ONE_BY_ONE;
        }
        
        SequenceStrategy strategy = strategyCache.get(mode);
        if (strategy == null) {
            // 如果找不到对应的策略，返回默认策略
            strategy = strategyCache.get(SequenceMode.ONE_BY_ONE);
        }
        
        return strategy;
    }
    
    /**
     * 检查指定的序列模式是否有对应的策略实现
     * 
     * @param mode 序列模式
     * @return 如果有策略返回 true，否则返回 false
     */
    public boolean hasStrategy(SequenceMode mode) {
        return mode != null && strategyCache.containsKey(mode);
    }
}


package com.chronoblocks.core.animation.effect.preset;

import com.chronoblocks.core.animation.ClipAnimationConfig;

import java.util.List;

/**
 * 特效预设包接口
 * 
 * <p>类似 Unity 的一个 VFX Prefab：可以一次性添加多个 EffectInstance。
 * 预设包封装了一组特效的组合配置，用户可以通过 UI 一键应用。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public interface EffectPreset {
    
    /**
     * 获取预设名称
     * 
     * @return 预设显示名称
     */
    String getName();
    
    /**
     * 获取预设描述
     * 
     * @return 预设描述文本
     */
    String getDescription();
    
    /**
     * 往指定 ClipAnimationConfig 添加多个特效实例
     * 
     * <p>此方法会创建并配置多个 EffectInstance，然后添加到 config 的 effects 列表中。
     * 
     * @param config 动画配置对象
     */
    void applyTo(ClipAnimationConfig config);
    
    /**
     * 可选：预设内部使用的标签，UI 可用来过滤
     * 
     * <p>例如：magic / sci-fi / nature / construction
     * 
     * @return 标签列表
     */
    default List<String> getTags() {
        return List.of();
    }
}


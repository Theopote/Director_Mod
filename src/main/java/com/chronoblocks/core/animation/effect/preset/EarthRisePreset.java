package com.chronoblocks.core.animation.effect.preset;

import com.chronoblocks.core.animation.ClipAnimationConfig;
import com.chronoblocks.core.animation.effect.BlockEffectRegistry;
import com.chronoblocks.core.animation.effect.EffectInstance;
import com.chronoblocks.core.animation.effect.EffectType;
import com.chronoblocks.core.animation.effect.IBlockEffect;

import java.util.List;

/**
 * 大地隆起预设包
 * 
 * <p>效果组合：
 * <ul>
 *   <li>强烈的泥土粒子爆裂</li>
 *   <li>大范围的烟尘效果</li>
 * </ul>
 * 
 * <p>适合场景：地形变形、土元素魔法、火山喷发
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class EarthRisePreset implements EffectPreset {
    
    @Override
    public String getName() {
        return "大地隆起 (Earth Rise)";
    }
    
    @Override
    public String getDescription() {
        return "模拟地面隆起/火山喷发的沉重感，适合地形变形、土元素魔法。";
    }
    
    @Override
    public List<String> getTags() {
        return List.of("nature", "earth", "magic", "destruction");
    }
    
    @Override
    public void applyTo(ClipAnimationConfig config) {
        EffectInstance burst = new EffectInstance(EffectType.DUST_BURST);
        burst.setStartOffset(0.0f);
        burst.setDuration(0.4f);
        burst.setEnabled(true);
        burst.setTriggerMode(EffectInstance.TriggerMode.ONE_SHOT);
        // 预设默认跟随片段阶段（出现/擦除都可用）
        burst.setPhase(config != null && config.getPhase() == ClipAnimationConfig.AnimationPhase.EXIT
                ? EffectInstance.EffectPhase.EXIT
                : EffectInstance.EffectPhase.ENTER);
        IBlockEffect burstImpl = BlockEffectRegistry.get(EffectType.DUST_BURST);
        if (burstImpl != null) {
            burst.getParams().putAll(burstImpl.getDefaultParams());
        }
        burst.setFloat("intensity", 2.5f);
        burst.setFloat("radius", 0.8f);
        config.addEffect(burst);
    }
}


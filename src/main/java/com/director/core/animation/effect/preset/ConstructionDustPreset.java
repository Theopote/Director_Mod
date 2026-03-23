package com.director.core.animation.effect.preset;

import com.director.core.animation.ClipAnimationConfig;
import com.director.core.animation.effect.BlockEffectRegistry;
import com.director.core.animation.effect.EffectInstance;
import com.director.core.animation.effect.EffectType;
import com.director.core.animation.effect.IBlockEffect;

import java.util.List;

/**
 * 建造烟尘预设包
 * 
 * <p>效果组合：
 * <ul>
 *   <li>轻微尘土效果</li>
 *   <li>落地时小爆裂</li>
 * </ul>
 * 
 * <p>适合场景：建筑工地、真实建筑效果
 * 
 * @author Director Team
 * @version 1.0
 */
public class ConstructionDustPreset implements EffectPreset {
    
    @Override
    public String getName() {
        return "建筑落地尘土 (Construction Dust)";
    }
    
    @Override
    public String getDescription() {
        return "建筑工地风格：轻微尘土 + 落地时小爆裂。";
    }
    
    @Override
    public List<String> getTags() {
        return List.of("construction", "realistic", "building");
    }
    
    @Override
    public void applyTo(ClipAnimationConfig config) {
        EffectInstance burst = new EffectInstance(EffectType.DUST_BURST);
        burst.setStartOffset(0.05f);
        burst.setDuration(0.2f);
        burst.setEnabled(true);
        burst.setTriggerMode(EffectInstance.TriggerMode.ONE_SHOT);
        burst.setPhase(config != null && config.getPhase() == ClipAnimationConfig.AnimationPhase.EXIT
                ? EffectInstance.EffectPhase.EXIT
                : EffectInstance.EffectPhase.ENTER);
        IBlockEffect burstImpl = BlockEffectRegistry.get(EffectType.DUST_BURST);
        if (burstImpl != null) {
            burst.getParams().putAll(burstImpl.getDefaultParams());
        }
        burst.setFloat("intensity", 1.0f);
        burst.setFloat("radius", 0.4f);
        if (config != null) {
            config.addEffect(burst);
        }
    }
}


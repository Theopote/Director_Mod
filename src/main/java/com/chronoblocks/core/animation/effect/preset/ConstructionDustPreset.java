package com.chronoblocks.core.animation.effect.preset;

import com.chronoblocks.core.animation.ClipAnimationConfig;
import com.chronoblocks.core.animation.effect.BlockEffectRegistry;
import com.chronoblocks.core.animation.effect.EffectInstance;
import com.chronoblocks.core.animation.effect.EffectType;
import com.chronoblocks.core.animation.effect.IBlockEffect;

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
 * @author ChronoBlocks Team
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
        IBlockEffect burstImpl = BlockEffectRegistry.get(EffectType.DUST_BURST);
        if (burstImpl != null) {
            burst.getParams().putAll(burstImpl.getDefaultParams());
        }
        burst.setFloat("intensity", 1.0f);
        burst.setFloat("radius", 0.4f);
        config.addEffect(burst);
    }
}


package com.chronoblocks.core.animation.effect.preset;

import com.chronoblocks.core.animation.ClipAnimationConfig;
import com.chronoblocks.core.animation.effect.BlockEffectRegistry;
import com.chronoblocks.core.animation.effect.EffectInstance;
import com.chronoblocks.core.animation.effect.EffectType;
import com.chronoblocks.core.animation.effect.IBlockEffect;

import java.util.List;

/**
 * 魔法召唤预设包
 * 
 * <p>效果组合：
 * <ul>
 *   <li>蓝色粒子凝聚（烟尘爆裂，小半径）</li>
 *   <li>出场闪白光（延迟触发）</li>
 * </ul>
 * 
 * <p>适合场景：魔法、传送门、召唤
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class MagicSummonPreset implements EffectPreset {
    
    @Override
    public String getName() {
        return "魔法召唤 (Magic Summon)";
    }
    
    @Override
    public String getDescription() {
        return "蓝色粒子凝聚 + 闪光 + 爆裂，适合魔法、传送门、召唤场景。";
    }
    
    @Override
    public List<String> getTags() {
        return List.of("magic", "fantasy", "summon");
    }
    
    @Override
    public void applyTo(ClipAnimationConfig config) {
        EffectInstance.EffectPhase phase = config != null && config.getPhase() == ClipAnimationConfig.AnimationPhase.EXIT
                ? EffectInstance.EffectPhase.EXIT
                : EffectInstance.EffectPhase.ENTER;

        // 1. 召唤粒子（蓝色/魔法粒子效果，用小半径烟尘模拟）
        EffectInstance conjure = new EffectInstance(EffectType.DUST_BURST);
        conjure.setStartOffset(0.0f);
        conjure.setDuration(0.25f);
        conjure.setEnabled(true);
        conjure.setTriggerMode(EffectInstance.TriggerMode.ONE_SHOT);
        conjure.setPhase(phase);
        // 设置默认参数
        IBlockEffect conjureImpl = BlockEffectRegistry.get(EffectType.DUST_BURST);
        if (conjureImpl != null) {
            conjure.getParams().putAll(conjureImpl.getDefaultParams());
        }
        // 覆盖预设特定参数
        conjure.setFloat("intensity", 1.5f);
        conjure.setFloat("radius", 0.3f);
        if (config != null) {
            config.addEffect(conjure);
        }

        // 2. 出场闪白光
        EffectInstance flash = new EffectInstance(EffectType.FLASH_WHITE);
        flash.setStartOffset(0.05f);
        flash.setDuration(0.20f);
        flash.setEnabled(true);
        flash.setTriggerMode(EffectInstance.TriggerMode.ONE_SHOT);
        flash.setPhase(phase);
        // 设置默认参数
        IBlockEffect flashImpl = BlockEffectRegistry.get(EffectType.FLASH_WHITE);
        if (flashImpl != null) {
            flash.getParams().putAll(flashImpl.getDefaultParams());
        }
        // 覆盖预设特定参数
        flash.setFloat("intensity", 1.2f);
        if (config != null) {
            config.addEffect(flash);
        }

        // 未来可以加：
        // - ArcaneCircleEffect
        // - SparkleRingEffect
    }
}


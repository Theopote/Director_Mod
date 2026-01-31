package com.chronoblocks.core.animation.effect.preset;

import com.chronoblocks.core.animation.ClipAnimationConfig;
import com.chronoblocks.core.animation.effect.BlockEffectRegistry;
import com.chronoblocks.core.animation.effect.EffectInstance;
import com.chronoblocks.core.animation.effect.EffectType;
import com.chronoblocks.core.animation.effect.IBlockEffect;

import java.util.List;

/**
 * 庄严传唤 / 神圣出场预设包
 * 
 * <p>
 * 效果组合：
 * <ul>
 * <li>强力闪白 (FLASH_WHITE)</li>
 * <li>魔法光焰环绕 (MAGIC_FLARE)</li>
 * <li>持续脉冲发光 (GLOW_PULSE)</li>
 * </ul>
 * 
 * <p>
 * 适合场景：神庙、魔法阵、重要遗迹出现
 */
public class HolySummonPreset implements EffectPreset {

    @Override
    public String getName() {
        return "庄严传唤 (Holy Summon)";
    }

    @Override
    public String getDescription() {
        return "神圣而庄严的出现效果，包含强光闪烁、魔法光焰和持续的神圣脉冲。";
    }

    @Override
    public List<String> getTags() {
        return List.of("magic", "holy", "energy", "bright");
    }

    @Override
    public void applyTo(ClipAnimationConfig config) {
        if (config == null)
            return;

        EffectInstance.EffectPhase phase = config.getPhase() == ClipAnimationConfig.AnimationPhase.EXIT
                ? EffectInstance.EffectPhase.EXIT
                : EffectInstance.EffectPhase.ENTER;

        // 1. 开场强光
        EffectInstance flash = new EffectInstance(EffectType.FLASH_WHITE);
        flash.setStartOffset(0.0f);
        flash.setDuration(0.2f);
        flash.setEnabled(true);
        flash.setTriggerMode(EffectInstance.TriggerMode.ONE_SHOT);
        flash.setPhase(phase);
        config.addEffect(flash);

        // 2. 持续魔法光焰
        EffectInstance flare = new EffectInstance(EffectType.MAGIC_FLARE);
        flare.setStartOffset(0.1f);
        flare.setDuration(0.8f);
        flare.setEnabled(true);
        flare.setTriggerMode(EffectInstance.TriggerMode.CONTINUOUS);
        flare.setPhase(phase);

        IBlockEffect flareImpl = BlockEffectRegistry.get(EffectType.MAGIC_FLARE);
        if (flareImpl != null)
            flare.getParams().putAll(flareImpl.getDefaultParams());
        flare.setFloat("intensity", 1.5f);
        flare.setFloat("spinSpeed", 2.5f);
        config.addEffect(flare);

        // 3. 柔和脉冲
        EffectInstance pulse = new EffectInstance(EffectType.GLOW_PULSE);
        pulse.setStartOffset(0.0f);
        pulse.setDuration(0.9f);
        pulse.setEnabled(true);
        pulse.setTriggerMode(EffectInstance.TriggerMode.CONTINUOUS);
        pulse.setPhase(phase);

        IBlockEffect pulseImpl = BlockEffectRegistry.get(EffectType.GLOW_PULSE);
        if (pulseImpl != null)
            pulse.getParams().putAll(pulseImpl.getDefaultParams());
        pulse.setFloat("intensity", 0.8f);
        pulse.setBoolean("cloud", true);
        config.addEffect(pulse);
    }
}

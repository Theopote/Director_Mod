package com.director.core.animation.effect.preset;

import com.director.core.animation.ClipAnimationConfig;
import com.director.core.animation.effect.BlockEffectRegistry;
import com.director.core.animation.effect.EffectInstance;
import com.director.core.animation.effect.EffectType;
import com.director.core.animation.effect.IBlockEffect;

import java.util.List;

/**
 * 重型着陆预设包
 * 
 * <p>
 * 效果组合：
 * <ul>
 * <li>大量烟尘爆裂 (DUST_BURST)</li>
 * <li>地面冲击扩散 (IMPACT_RIPPLE)</li>
 * </ul>
 * 
 * <p>
 * 适合场景：巨石坠落、建筑整体落地
 */
public class HeavyImpactPreset implements EffectPreset {

    @Override
    public String getName() {
        return "重型着陆 (Heavy Impact)";
    }

    @Override
    public String getDescription() {
        return "模拟沉重物体落地的剧烈冲击，包含大量扬尘和地面震荡波。";
    }

    @Override
    public List<String> getTags() {
        return List.of("heavy", "land", "impact", "physical");
    }

    @Override
    public void applyTo(ClipAnimationConfig config) {
        if (config == null)
            return;

        // 重型着陆通常在 EXIT 动画结束前后，或者 ENTER 动画结束时触发
        // 这里基于 ENTER 逻辑实现（落地）
        EffectInstance.EffectPhase phase = config.getPhase() == ClipAnimationConfig.AnimationPhase.EXIT
                ? EffectInstance.EffectPhase.EXIT
                : EffectInstance.EffectPhase.ENTER;

        // 1. 强力烟尘
        EffectInstance dust = new EffectInstance(EffectType.DUST_BURST);
        // 如果是进入动画，延迟到动画结束（落地）触发
        dust.setStartOffset(0.0f);
        dust.setDuration(0.3f);
        dust.setEnabled(true);
        dust.setTriggerMode(EffectInstance.TriggerMode.ONE_SHOT);
        dust.setPhase(phase);

        IBlockEffect dustImpl = BlockEffectRegistry.get(EffectType.DUST_BURST);
        if (dustImpl != null)
            dust.getParams().putAll(dustImpl.getDefaultParams());
        dust.setFloat("intensity", 2.5f);
        dust.setFloat("radius", 1.2f);
        config.addEffect(dust);

        // 2. 落地涟漪
        EffectInstance ripple = new EffectInstance(EffectType.IMPACT_RIPPLE);
        ripple.setStartOffset(0.0f);
        ripple.setDuration(0.4f);
        ripple.setEnabled(true);
        ripple.setTriggerMode(EffectInstance.TriggerMode.ONE_SHOT);
        ripple.setPhase(phase);

        IBlockEffect rippleImpl = BlockEffectRegistry.get(EffectType.IMPACT_RIPPLE);
        if (rippleImpl != null)
            ripple.getParams().putAll(rippleImpl.getDefaultParams());
        ripple.setFloat("radius", 2.0f);
        ripple.setFloat("intensity", 1.5f);
        config.addEffect(ripple);
    }
}

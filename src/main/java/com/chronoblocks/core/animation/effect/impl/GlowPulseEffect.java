package com.chronoblocks.core.animation.effect.impl;

import com.chronoblocks.core.animation.effect.EffectInstance;
import com.chronoblocks.core.animation.effect.IBlockEffect;
import com.chronoblocks.core.animation.effect.ui.EffectUIHelper;
import com.chronoblocks.core.animation.effect.ui.EffectUIUtils;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 脉冲发光：用少量“光点 + 轻雾”模拟方块出现时的短暂发光脉冲。
 *
 * <p>注意：当前 Effect API 不直接修改渲染材质/光照，因此使用粒子来表现“发光感”。</p>
 *
 * <p>参数：</p>
 * <ul>
 *   <li>intensity (float, 默认 1.0) - 强度（粒子数量倍率）</li>
 *   <li>radius (float, 默认 0.45) - 扩散半径</li>
 *   <li>pulseCount (int, 默认 2) - 一个窗口内脉冲次数</li>
 *   <li>cloud (boolean, 默认 true) - 是否附加少量 CLOUD 粒子增强“发光雾”</li>
 * </ul>
 */
public class GlowPulseEffect implements IBlockEffect {

    @Override
    public void render(ClientWorld world, BlockPos pos, float t, EffectInstance inst, Random random) {
        if (world == null || pos == null || inst == null) {
            return;
        }

        float intensity = EffectUIUtils.getFloat(inst.getParams(), "intensity", 0.6f);
        float radius = EffectUIUtils.getFloat(inst.getParams(), "radius", 0.45f);
        int pulseCount = Math.max(1, EffectUIUtils.getInt(inst.getParams(), "pulseCount", 2));
        boolean cloud = EffectUIUtils.getBoolean(inst.getParams(), "cloud", false);

        // 脉冲：0..1 内做 pulseCount 次 sin 波
        double wave = Math.sin(t * Math.PI * 2.0 * pulseCount);
        double pulse = Math.max(0.0, wave); // 只取正半波，让“亮→暗”更明确

        // 强度随脉冲衰减：避免持续刷
        int base = (int) Math.round((2.0 + 5.0 * pulse) * intensity);
        int count = Math.max(1, Math.min(base, 12)); // 上限更低：默认更克制

        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;

        for (int i = 0; i < count; i++) {
            double ox = cx + (random.nextDouble() - 0.5) * radius * 2.0;
            double oy = cy + (random.nextDouble() - 0.5) * radius * 1.2;
            double oz = cz + (random.nextDouble() - 0.5) * radius * 2.0;

            double vx = (random.nextDouble() - 0.5) * 0.01;
            double vy = 0.01 + random.nextDouble() * 0.02;
            double vz = (random.nextDouble() - 0.5) * 0.01;

            world.addParticleClient(ParticleTypes.END_ROD, ox, oy, oz, vx, vy, vz);
        }

        // 额外雾化：非常克制
        if (cloud && pulse > 0.55) {
            int cloudCount = Math.max(0, Math.min(1, (int) Math.round(intensity)));
            for (int i = 0; i < cloudCount; i++) {
                double ox = cx + (random.nextDouble() - 0.5) * 0.3;
                double oy = cy + (random.nextDouble() - 0.5) * 0.3;
                double oz = cz + (random.nextDouble() - 0.5) * 0.3;
                world.addParticleClient(ParticleTypes.CLOUD, ox, oy, oz, 0.0, 0.0, 0.0);
            }
        }
    }

    @Override
    public Map<String, Object> getDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        // 默认更“克制”：强度更低，且默认不附加 CLOUD
        params.put("intensity", 0.6f);
        params.put("radius", 0.45f);
        params.put("pulseCount", 2);
        params.put("cloud", false);
        return params;
    }

    @Override
    public boolean renderUI(Map<String, Object> params) {
        if (!EffectUIHelper.isUIAvailable()) {
            return false;
        }
        boolean modified = false;

        EffectUIHelper.label("脉冲发光参数");

        float intensity = EffectUIUtils.getFloat(params, "intensity", 1.0f);
        float[] intensityArr = {intensity};
        if (EffectUIHelper.dragFloat("强度 (Intensity)", intensityArr, 0.1f, 0.0f, 5.0f, "%.1f")) {
            params.put("intensity", intensityArr[0]);
            modified = true;
        }

        float radius = EffectUIUtils.getFloat(params, "radius", 0.45f);
        float[] radiusArr = {radius};
        if (EffectUIHelper.dragFloat("半径 (Radius)", radiusArr, 0.02f, 0.05f, 2.0f, "%.2f")) {
            params.put("radius", radiusArr[0]);
            modified = true;
        }

        int pulseCount = Math.max(1, EffectUIUtils.getInt(params, "pulseCount", 2));
        float[] pulseAsFloat = {(float) pulseCount};
        if (EffectUIHelper.dragFloat("脉冲次数 (PulseCount)", pulseAsFloat, 1.0f, 1.0f, 10.0f, "%.0f")) {
            params.put("pulseCount", Math.max(1, Math.round(pulseAsFloat[0])));
            modified = true;
        }

        boolean cloud = EffectUIUtils.getBoolean(params, "cloud", true);
        boolean[] cloudArr = {cloud};
        if (EffectUIHelper.checkbox("附加轻雾 (Cloud)", cloudArr)) {
            params.put("cloud", cloudArr[0]);
            modified = true;
        }

        return modified;
    }
}



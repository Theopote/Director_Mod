package com.chronoblocks.core.animation.effect.impl;

import com.chronoblocks.core.animation.BlockAnimationSnapshot;
import com.chronoblocks.core.animation.effect.EffectInstance;
import com.chronoblocks.core.animation.effect.IBlockEffect;
import com.chronoblocks.core.animation.effect.ui.EffectUIHelper;
import com.chronoblocks.core.animation.effect.ui.EffectUIUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 落地涟漪：在方块落地瞬间产生的水平面扩张粒子效果。
 * 
 * <p>
 * 参数指标：
 * </p>
 * <ul>
 * <li>radius (float, 默认 0.8) - 最终扩散半径</li>
 * <li>intensity (float, 默认 1.0) - 粒子密度</li>
 * <li>heightOffset (float, 默认 0.05) - 涟漪离地的偏移高度（防闪烁）</li>
 * </ul>
 */
public class ImpactRippleEffect implements IBlockEffect {

    @Override
    public void render(ClientWorld world, BlockAnimationSnapshot snapshot, float t, EffectInstance inst,
            Random random) {
        if (world == null || snapshot == null || inst == null) {
            return;
        }

        BlockPos pos = snapshot.getPos();

        float radius = EffectUIUtils.getFloat(inst.getParams(), "radius", 0.8f);
        float intensity = EffectUIUtils.getFloat(inst.getParams(), "intensity", 1.0f);
        float heightOffset = EffectUIUtils.getFloat(inst.getParams(), "heightOffset", 0.05f);

        // 涟漪半径随时间扩张：0 -> 1.0
        float currentRadius = radius * t;

        // 强度随时间衰减：t=0 时最密，t=1.0 时几乎消失
        float fade = 1.0f - t;
        int count = (int) (12 * intensity * fade);
        if (count <= 0)
            return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.particleManager == null)
            return;
        ParticleManager particleManager = client.particleManager;

        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + heightOffset; // 贴地高度
        double cz = pos.getZ() + 0.5;

        for (int i = 0; i < count; i++) {
            // 在当前半径圆周上随机分布
            double angle = random.nextDouble() * Math.PI * 2.0;
            // 增加一点径向抖动，让涟漪看起来不那么单薄
            double r = currentRadius * (1.0 + (random.nextDouble() - 0.5) * 0.15);

            double ox = cx + Math.cos(angle) * r;
            double oz = cz + Math.sin(angle) * r;

            // 涟漪粒子基本不动，或轻微向外漂移
            double vx = Math.cos(angle) * 0.01;
            double vz = Math.sin(angle) * 0.01;
            double vy = 0.01;

            particleManager.addParticle(ParticleTypes.CLOUD, ox, cy, oz, vx, vy, vz);
        }
    }

    @Override
    public Map<String, Object> getDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("radius", 0.8f);
        params.put("intensity", 1.0f);
        params.put("heightOffset", 0.05f);
        return params;
    }

    @Override
    public boolean renderUI(Map<String, Object> params) {
        if (!EffectUIHelper.isUIAvailable())
            return false;
        boolean modified = false;

        EffectUIHelper.label("落地涟漪参数");

        float radius = EffectUIUtils.getFloat(params, "radius", 0.8f);
        float[] rArr = { radius };
        if (EffectUIHelper.dragFloat("扩散半径 (Radius)", rArr, 0.05f, 0.1f, 3.0f, "%.2f")) {
            params.put("radius", rArr[0]);
            modified = true;
        }

        float intensity = EffectUIUtils.getFloat(params, "intensity", 1.0f);
        float[] iArr = { intensity };
        if (EffectUIHelper.dragFloat("强度 (Intensity)", iArr, 0.1f, 0.0f, 5.0f, "%.1f")) {
            params.put("intensity", iArr[0]);
            modified = true;
        }

        float hOffset = EffectUIUtils.getFloat(params, "heightOffset", 0.05f);
        float[] hArr = { hOffset };
        if (EffectUIHelper.dragFloat("离地高度 (Y Offset)", hArr, 0.01f, -0.5f, 0.5f, "%.2f")) {
            params.put("heightOffset", hArr[0]);
            modified = true;
        }

        return modified;
    }
}

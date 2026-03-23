package com.director.core.animation.effect.impl;

import com.director.core.animation.BlockAnimationSnapshot;
import com.director.core.animation.effect.EffectInstance;
import com.director.core.animation.effect.IBlockEffect;
import com.director.core.animation.effect.ui.EffectUIHelper;
import com.director.core.animation.effect.ui.EffectUIUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 魔法光焰特效：围绕方块中心缓慢旋转的光点（低频、可持续）。
 *
 * <p>
 * 为了兼容不同 MC 版本，这里使用 END_ROD 粒子模拟“魔法光焰”。
 * </p>
 *
 * <p>
 * 参数：
 * </p>
 * <ul>
 * <li>intensity (float, 默认 1.0) - 粒子数量倍率</li>
 * <li>radius (float, 默认 0.55) - 环绕半径</li>
 * <li>height (float, 默认 0.55) - 竖直抖动范围</li>
 * <li>spinSpeed (float, 默认 1.5) - 旋转速度（圈/秒，近似）</li>
 * </ul>
 */
public class MagicFlareEffect implements IBlockEffect {

    @Override
    public void render(ClientWorld world, BlockAnimationSnapshot snapshot, float t, EffectInstance inst,
            Random ignored) {
        if (world == null || snapshot == null || inst == null) {
            return;
        }

        BlockPos pos = snapshot.getPos();

        // 🚀 确定性随机种子
        long seed = pos.asLong() ^ (long) (t * 1000000L);
        Random deterministicRandom = new Random(seed);

        float intensity = EffectUIUtils.getFloat(inst.getParams(), "intensity", 0.6f);
        float radius = EffectUIUtils.getFloat(inst.getParams(), "radius", 0.5f);
        float height = EffectUIUtils.getFloat(inst.getParams(), "height", 0.45f);
        float spinSpeed = EffectUIUtils.getFloat(inst.getParams(), "spinSpeed", 1.2f);

        // 轻量：每帧只刷少量粒子（连续触发也不会炸）
        int count = Math.max(1, Math.min(3, (int) Math.ceil(1.5f * intensity)));

        // ✅ 修复：使用 particleManager.addParticle() 而不是 addParticleClient()
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.particleManager == null) {
            return;
        }
        ParticleManager particleManager = client.particleManager;

        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;

        // 使用 t 做相位，让同一个方块在一个动画窗口内呈现“旋转感”
        double baseAngle = (t * (Math.PI * 2.0) * spinSpeed);

        for (int i = 0; i < count; i++) {
            double jitter = (deterministicRandom.nextDouble() - 0.5) * 0.35;
            double a = baseAngle + jitter + i * (Math.PI * 2.0 / count);

            double r = radius * (0.85 + deterministicRandom.nextDouble() * 0.3);
            double ox = cx + Math.cos(a) * r;
            double oz = cz + Math.sin(a) * r;
            double oy = cy + (deterministicRandom.nextDouble() - 0.5) * height;

            // 轻微上飘 + 环绕切向速度
            double vx = -Math.sin(a) * 0.01;
            double vz = Math.cos(a) * 0.01;
            double vy = 0.01 + deterministicRandom.nextDouble() * 0.01;

            // ✅ 修复：使用 ENCHANT 粒子来模拟魔法光焰效果（紫色魔法粒子）
            particleManager.addParticle(ParticleTypes.ENCHANT, ox, oy, oz, vx, vy, vz);
        }
    }

    @Override
    public Map<String, Object> getDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        // 默认更“克制”：强度更低、半径更小、抖动更少
        params.put("intensity", 0.6f);
        params.put("radius", 0.5f);
        params.put("height", 0.45f);
        params.put("spinSpeed", 1.2f);
        return params;
    }

    @Override
    public boolean renderUI(Map<String, Object> params) {
        if (!EffectUIHelper.isUIAvailable()) {
            return false;
        }
        boolean modified = false;

        EffectUIHelper.label("魔法光焰参数");

        float intensity = EffectUIUtils.getFloat(params, "intensity", 1.0f);
        float[] intensityArr = { intensity };
        if (EffectUIHelper.dragFloat("强度 (Intensity)", intensityArr, 0.1f, 0.0f, 5.0f, "%.1f")) {
            params.put("intensity", intensityArr[0]);
            modified = true;
        }

        float radius = EffectUIUtils.getFloat(params, "radius", 0.55f);
        float[] radiusArr = { radius };
        if (EffectUIHelper.dragFloat("半径 (Radius)", radiusArr, 0.02f, 0.05f, 2.0f, "%.2f")) {
            params.put("radius", radiusArr[0]);
            modified = true;
        }

        float height = EffectUIUtils.getFloat(params, "height", 0.55f);
        float[] heightArr = { height };
        if (EffectUIHelper.dragFloat("高度抖动 (Height)", heightArr, 0.02f, 0.0f, 2.0f, "%.2f")) {
            params.put("height", heightArr[0]);
            modified = true;
        }

        float spinSpeed = EffectUIUtils.getFloat(params, "spinSpeed", 1.5f);
        float[] speedArr = { spinSpeed };
        if (EffectUIHelper.dragFloat("旋转速度 (Spin)", speedArr, 0.1f, 0.0f, 10.0f, "%.1f")) {
            params.put("spinSpeed", speedArr[0]);
            modified = true;
        }

        return modified;
    }
}

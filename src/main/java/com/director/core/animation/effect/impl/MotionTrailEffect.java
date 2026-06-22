package com.director.core.animation.effect.impl;

import com.director.core.animation.BlockAnimationSnapshot;
import com.director.core.animation.effect.EffectInstance;
import com.director.core.animation.effect.IBlockEffect;
import com.director.core.animation.effect.EffectType;
import com.director.core.animation.effect.ui.EffectUIHelper;
import com.director.core.animation.effect.ui.EffectUIUtils;
import com.director.i18n.EffectUiI18n;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 位移尾迹：当方块处于动态位移中时，在其视觉中心留下跟随的粒子轨迹。
 * 
 * <p>
 * 利用 BlockAnimationSnapshot 中的 transform 矩阵计算真实的视觉坐标。
 * </p>
 */
public class MotionTrailEffect implements IBlockEffect {

    @Override
    public void render(ClientWorld world, BlockAnimationSnapshot snapshot, float t, EffectInstance inst,
            Random ignored) {
        if (world == null || snapshot == null || inst == null)
            return;

        // 🚀 确定性随机种子
        long seed = snapshot.getPos().asLong() ^ (long) (t * 1000000L);
        Random deterministicRandom = new Random(seed);

        float intensity = EffectUIUtils.getFloat(inst.getParams(), "intensity", 1.0f);
        float density = EffectUIUtils.getFloat(inst.getParams(), "density", 1.0f);

        // 只有当方块可见且 alpha 足够时才生成
        if (!snapshot.isVisible() || snapshot.getAlpha() < 0.1f)
            return;

        // 计算当前视觉中心的世界坐标
        double worldX, worldY, worldZ;
        BlockPos basePos = snapshot.getPos();

        MatrixStack transform = snapshot.getTransform();
        if (transform != null) {
            Matrix4f matrix = transform.peek().getPositionMatrix();
            // 方块中心点 (0.5, 0.5, 0.5)
            Vector4f center = new Vector4f(0.5f, 0.5f, 0.5f, 1.0f);
            center.mul(matrix);

            worldX = basePos.getX() + center.x();
            worldY = basePos.getY() + center.y();
            worldZ = basePos.getZ() + center.z();
        } else {
            // 无变换，直接用 basePos 中心
            worldX = basePos.getX() + 0.5;
            worldY = basePos.getY() + 0.5;
            worldZ = basePos.getZ() + 0.5;
        }

        int count = (int) (3 * intensity * density);
        if (count <= 0)
            return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.particleManager == null)
            return;
        ParticleManager particleManager = client.particleManager;

        for (int i = 0; i < count; i++) {
            // 在中心附近随机偏移
            double ox = worldX + (deterministicRandom.nextDouble() - 0.5) * 0.4;
            double oy = worldY + (deterministicRandom.nextDouble() - 0.5) * 0.4;
            double oz = worldZ + (deterministicRandom.nextDouble() - 0.5) * 0.4;

            // 尾迹粒子产生后几乎静止，或者有少许随机漂移
            double vx = (deterministicRandom.nextDouble() - 0.5) * 0.02;
            double vy = (deterministicRandom.nextDouble() - 0.5) * 0.02;
            double vz = (deterministicRandom.nextDouble() - 0.5) * 0.02;

            particleManager.addParticle(ParticleTypes.ELECTRIC_SPARK, ox, oy, oz, vx, vy, vz);
            if (deterministicRandom.nextDouble() < 0.3) {
                particleManager.addParticle(ParticleTypes.ASH, ox, oy, oz, 0, 0.01, 0);
            }
        }
    }

    @Override
    public Map<String, Object> getDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("intensity", 1.0f);
        params.put("density", 1.0f);
        return params;
    }

    @Override
    public boolean renderUI(Map<String, Object> params) {
        if (!EffectUIHelper.isUIAvailable())
            return false;
        boolean modified = false;

        EffectUIHelper.label(EffectUiI18n.section(EffectType.MOTION_TRAIL));

        float intensity = EffectUIUtils.getFloat(params, "intensity", 1.0f);
        float[] iArr = { intensity };
        if (EffectUIHelper.dragFloat(EffectUiI18n.intensity(), iArr, 0.1f, 0.0f, 5.0f, "%.1f")) {
            params.put("intensity", iArr[0]);
            modified = true;
        }

        float density = EffectUIUtils.getFloat(params, "density", 1.0f);
        float[] dArr = { density };
        if (EffectUIHelper.dragFloat(EffectUiI18n.density(), dArr, 0.1f, 0.0f, 5.0f, "%.1f")) {
            params.put("density", dArr[0]);
            modified = true;
        }

        return modified;
    }
}

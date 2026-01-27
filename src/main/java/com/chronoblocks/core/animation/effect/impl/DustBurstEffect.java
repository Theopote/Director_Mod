package com.chronoblocks.core.animation.effect.impl;

import com.chronoblocks.core.animation.effect.EffectInstance;
import com.chronoblocks.core.animation.effect.IBlockEffect;
import com.chronoblocks.core.animation.effect.ui.EffectUIHelper;
import com.chronoblocks.core.animation.effect.ui.EffectUIUtils;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 落地烟尘爆裂特效。
 * 
 * <p>参数：
 * <ul>
 *   <li>intensity (float, 默认 1.0) 决定烟尘数量</li>
 *   <li>radius (float, 默认 0.6) 决定散射半径</li>
 * </ul>
 * 
 * <p>典型场景：方块"掉落/升起"结束时，在底部蹦起一点灰尘。
 * 这里假定你多数情况把效果挂在动画开始附近；
 * 如果你要"落地时爆尘"，可以让 UI 把 startOffset 设置为 config.getDuration() 附近。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class DustBurstEffect implements IBlockEffect {
    
    @Override
    public void render(ClientWorld world,
                       BlockPos pos,
                       float t,
                       EffectInstance inst,
                       Random random) {
        
        // ✅ 修复：对于 ONE_SHOT 模式，t 总是 0.0f，应该允许触发
        // 只在前 40% 的时间里触发爆炸感，避免持续不断地放粒子
        // 但对于 ONE_SHOT 模式，即使 t=0.0f 也应该触发一次
        if (inst.getTriggerMode() == EffectInstance.TriggerMode.ONE_SHOT) {
            // ONE_SHOT 模式：总是触发（t 已经被设置为 0.0f）
            // 不做任何检查，直接继续
        } else {
            // CONTINUOUS 模式：只在前 40% 的时间里触发
            if (t > 0.4f) {
                return;
            }
        }
        
        float intensity = EffectUIUtils.getFloat(inst.getParams(), "intensity", 1.0f);
        float radius = EffectUIUtils.getFloat(inst.getParams(), "radius", 0.6f);
        
        // t 越小爆得越猛
        float burstPhase = 1.0f - (t / 0.4f);
        if (burstPhase < 0) burstPhase = 0;
        
        int count = Math.max(2, (int) (12 * intensity * burstPhase));
        
        // 使用方块本身材质的粒子效果，如果取不到就退回 POOF
        ParticleEffect effect;
        try {
            effect = new BlockStateParticleEffect(
                    ParticleTypes.BLOCK,
                    world.getBlockState(pos)
            );
        } catch (Exception e) {
            effect = ParticleTypes.POOF;
        }
        
        double baseX = pos.getX() + 0.5;
        double baseY = pos.getY() + 0.01; // 靠近地面
        double baseZ = pos.getZ() + 0.5;
        
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double r = radius * random.nextDouble();
            
            double ox = baseX + Math.cos(angle) * r;
            double oz = baseZ + Math.sin(angle) * r;
            double oy = baseY + random.nextDouble() * 0.2;
            
            double vx = (ox - baseX) * 0.15;
            double vy = 0.1 + random.nextDouble() * 0.1;
            double vz = (oz - baseZ) * 0.15;
            
            world.addParticleClient(effect, ox, oy, oz, vx, vy, vz);
        }
    }
    
    @Override
    public Map<String, Object> getDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("intensity", 1.0f);
        params.put("radius", 0.6f);
        return params;
    }
    
    @Override
    public boolean renderUI(Map<String, Object> params) {
        if (!EffectUIHelper.isUIAvailable()) {
            return false; // UI 不可用
        }
        
        boolean modified = false;
        
        EffectUIHelper.label("烟尘爆裂参数");
        
        // 强度参数
        float intensity = EffectUIUtils.getFloat(params, "intensity", 1.0f);
        float[] intensityArr = {intensity};
        if (EffectUIHelper.dragFloat("强度 (Intensity)", intensityArr, 0.1f, 0.0f, 5.0f, "%.1f")) {
            params.put("intensity", intensityArr[0]);
            modified = true;
        }
        if (EffectUIHelper.isItemHovered()) {
            EffectUIHelper.setTooltip("控制烟尘粒子的产生数量");
        }
        
        // 半径参数
        float radius = EffectUIUtils.getFloat(params, "radius", 0.6f);
        float[] radiusArr = {radius};
        if (EffectUIHelper.dragFloat("散射半径 (Radius)", radiusArr, 0.05f, 0.1f, 3.0f, "%.2f")) {
            params.put("radius", radiusArr[0]);
            modified = true;
        }
        if (EffectUIHelper.isItemHovered()) {
            EffectUIHelper.setTooltip("控制烟尘飞散的水平范围");
        }
        
        return modified;
    }
}

package com.chronoblocks.core.animation.effect.impl;

import com.chronoblocks.core.animation.effect.EffectInstance;
import com.chronoblocks.core.animation.effect.IBlockEffect;
import com.chronoblocks.core.animation.effect.ui.EffectUIHelper;
import com.chronoblocks.core.animation.effect.ui.EffectUIUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 方块出现瞬间的闪白高光效果。
 * 
 * <p>参数：
 * <ul>
 *   <li>intensity (float, 默认 1.0) 决定粒子数量</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class FlashWhiteEffect implements IBlockEffect {
    
    @Override
    public void render(ClientWorld world,
                       BlockPos pos,
                       float t,
                       EffectInstance inst,
                       Random random) {
        
        // t: [0,1]，只在前 0.3 内明显
        if (t > 0.3f) {
            return;
        }
        
        float intensity = EffectUIUtils.getFloat(inst.getParams(), "intensity", 1.0f);
        // t 越接近 0，闪得越猛
        float fade = 1.0f - (t / 0.3f);
        if (fade < 0) fade = 0;
        
        int baseCount = Math.max(1, (int) (4 * intensity * fade));
        
        // ✅ 修复：使用 particleManager.addParticle() 而不是 addParticleClient()
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.particleManager == null) {
            return;
        }
        ParticleManager particleManager = client.particleManager;
        
        for (int i = 0; i < baseCount; i++) {
            double ox = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
            double oy = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
            double oz = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
            
            // ✅ 修复：使用 CRIT 粒子来模拟闪白光效果（更明显的白色闪光）
            particleManager.addParticle(
                    ParticleTypes.CRIT,
                    ox, oy, oz,
                    0.0, 0.02, 0.0
            );
        }
    }
    
    @Override
    public Map<String, Object> getDefaultParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("intensity", 1.0f);
        return params;
    }
    
    @Override
    public boolean renderUI(Map<String, Object> params) {
        if (!EffectUIHelper.isUIAvailable()) {
            return false; // UI 不可用
        }
        
        boolean modified = false;
        
        EffectUIHelper.label("闪白光参数");
        
        float intensity = EffectUIUtils.getFloat(params, "intensity", 1.0f);
        float[] intensityArr = {intensity};
        if (EffectUIHelper.dragFloat("强度 (Intensity)", intensityArr, 0.1f, 0.0f, 5.0f, "%.1f")) {
            params.put("intensity", intensityArr[0]);
            modified = true;
        }
        if (EffectUIHelper.isItemHovered()) {
            EffectUIHelper.setTooltip("控制闪白光的强度（粒子数量）");
        }
        
        return modified;
    }
}

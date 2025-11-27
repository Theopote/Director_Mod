package com.chronoblocks.core.animation.effect.preset;

import com.chronoblocks.core.animation.ClipAnimationConfig;
import com.chronoblocks.core.animation.effect.BlockEffectRegistry;
import com.chronoblocks.core.animation.effect.EffectInstance;
import com.chronoblocks.core.animation.effect.EffectType;
import com.chronoblocks.core.animation.effect.IBlockEffect;

import java.util.List;

/**
 * 科幻扫描/组装预设包
 * 
 * <p>效果组合：
 * <ul>
 *   <li>扫描线效果（小半径粒子）</li>
 *   <li>微光闪烁（闪白光）</li>
 * </ul>
 * 
 * <p>适合场景：科技、生化舱、机器人组装
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class SciFiAssemblePreset implements EffectPreset {
    
    @Override
    public String getName() {
        return "科幻扫描 / 组装 (Sci-Fi Assemble)";
    }
    
    @Override
    public String getDescription() {
        return "科幻扫描线 + 蓝色粒子，适合科技、生化舱、机器人组装场景。";
    }
    
    @Override
    public List<String> getTags() {
        return List.of("sci-fi", "tech", "futuristic");
    }
    
    @Override
    public void applyTo(ClipAnimationConfig config) {
        // 扫描线：用 DustBurst 半径非常小模拟点亮
        EffectInstance scan = new EffectInstance(EffectType.DUST_BURST);
        scan.setStartOffset(0.0f);
        scan.setDuration(0.15f);
        scan.setEnabled(true);
        IBlockEffect scanImpl = BlockEffectRegistry.get(EffectType.DUST_BURST);
        if (scanImpl != null) {
            scan.getParams().putAll(scanImpl.getDefaultParams());
        }
        scan.setFloat("intensity", 1.2f);
        scan.setFloat("radius", 0.1f);
        config.addEffect(scan);
        
        // 微光闪烁（直接用 FlashWhite）
        EffectInstance flash = new EffectInstance(EffectType.FLASH_WHITE);
        flash.setStartOffset(0.05f);
        flash.setDuration(0.15f);
        flash.setEnabled(true);
        IBlockEffect flashImpl = BlockEffectRegistry.get(EffectType.FLASH_WHITE);
        if (flashImpl != null) {
            flash.getParams().putAll(flashImpl.getDefaultParams());
        }
        flash.setFloat("intensity", 0.8f);
        config.addEffect(flash);
    }
}


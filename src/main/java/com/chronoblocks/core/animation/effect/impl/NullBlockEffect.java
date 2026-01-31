package com.chronoblocks.core.animation.effect.impl;

import com.chronoblocks.core.animation.BlockAnimationSnapshot;
import com.chronoblocks.core.animation.effect.EffectInstance;
import com.chronoblocks.core.animation.effect.IBlockEffect;
import net.minecraft.client.world.ClientWorld;

import java.util.Collections;
import java.util.Map;
import java.util.Random;

/**
 * 空对象模式实现
 * 
 * <p>
 * 用于防止 UI 渲染时空指针崩溃，当找不到特效实现时返回此对象
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class NullBlockEffect implements IBlockEffect {

    private static final NullBlockEffect INSTANCE = new NullBlockEffect();

    private NullBlockEffect() {
    }

    public static NullBlockEffect getInstance() {
        return INSTANCE;
    }

    @Override
    public void render(ClientWorld world, BlockAnimationSnapshot snapshot, float t, EffectInstance inst,
            Random random) {
        // 空实现：什么都不做
    }

    @Override
    public Map<String, Object> getDefaultParams() {
        return Collections.emptyMap();
    }

    @Override
    public boolean renderUI(Map<String, Object> params) {
        // 空实现：不渲染任何 UI
        return false;
    }
}

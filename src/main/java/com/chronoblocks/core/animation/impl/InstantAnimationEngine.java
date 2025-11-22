package com.chronoblocks.core.animation.impl;

import com.chronoblocks.core.animation.BlockAnimationEngine;
import com.chronoblocks.core.container.AnimationMode;
import com.chronoblocks.core.container.Container;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * 瞬间出现动画引擎
 * 
 * <p>所有方块立即在片段开始时间显示
 */
public class InstantAnimationEngine implements BlockAnimationEngine {
    
    @Override
    public float calculateBlockAppearTime(
        Container container,
        BlockPos blockPos,
        float clipStartTime,
        float clipDuration,
        float animationDuration,
        Map<String, Object> params
    ) {
        // 瞬间出现：所有方块在片段开始时立即显示
        return clipStartTime;
    }
    
    @Override
    public AnimationMode[] getSupportedModes() {
        return new AnimationMode[] { AnimationMode.INSTANT };
    }
    
    @Override
    public String getEngineName() {
        return "InstantAnimation";
    }
}


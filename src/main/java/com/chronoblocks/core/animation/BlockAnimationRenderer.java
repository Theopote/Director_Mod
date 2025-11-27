package com.chronoblocks.core.animation;

import com.chronoblocks.core.animation.effect.BlockEffectRegistry;
import com.chronoblocks.core.animation.effect.EffectInstance;
import com.chronoblocks.core.animation.effect.IBlockEffect;
import com.chronoblocks.core.animation.sequence.BlockSequenceEntry;
import com.chronoblocks.core.animation.transform.AnimationTransformerFactory;
import com.chronoblocks.core.animation.transform.IAnimationTransformer;
import com.chronoblocks.core.animation.transform.IAnimationTransformer.TransformResult;
import com.chronoblocks.core.animation.easing.EasingFunction;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * BlockAnimationRenderer
 * 
 * <p>负责执行单个方块的完整渲染流程，包括：
 * <ul>
 *   <li>1. 序列行为 → 映射到绝对时间点（normalizedSeqTime）</li>
 *   <li>2. 动画行为 → 计算局部动画时间 → 应用 Easing → 应用 Transformer</li>
 *   <li>3. 特效系统 → 计算每个特效的局部时间 → 调用 effect.render()</li>
 *   <li>4. 渲染方块本体</li>
 * </ul>
 * 
 * <p>时间映射流程：
 * <pre>
 * elapsedTime (全局时间)
 *   ↓
 * normalizedSeqTime → seqStartTime (方块开始时间)
 *   ↓
 * localAnimTime (方块局部时间)
 *   ↓
 * animProgress [0..1] → easedT [0..1] → Transformer
 *   ↓
 * 特效时间映射：localAnimTime - effectStartOffset → effectT [0..1] → Effect
 * </pre>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class BlockAnimationRenderer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockAnimationRenderer.class);
    
    /**
     * 生成基于方块位置的稳定随机数生成器
     * 
     * @param pos 方块位置
     * @return 稳定随机数生成器
     */
    private static Random stableRandom(BlockPos pos) {
        long seed = pos.asLong() * 4987142L + 5947611L;
        return new Random(seed);
    }
    
    /**
     * 主入口：渲染方块
     * 
     * <p>完整的渲染流程：
     * <ol>
     *   <li>计算方块开始时间（基于序列行为）</li>
     *   <li>计算局部动画时间和进度</li>
     *   <li>应用缓动函数</li>
     *   <li>应用动画变换（位移/旋转/缩放）</li>
     *   <li>执行特效渲染</li>
     *   <li>渲染方块本体</li>
     * </ol>
     * 
     * @param world 客户端世界
     * @param matrices MatrixStack（会被修改以应用变换）
     * @param consumers VertexConsumerProvider
     * @param pos 方块坐标
     * @param seqEntry 序列信息（包含 normalizedSeqTime）
     * @param clip 时间片段信息
     * @param elapsedTime 当前播放到了 clip 内的多少秒（已考虑 Timeline）
     */
    public static void renderBlock(
            ClientWorld world,
            MatrixStack matrices,
            VertexConsumerProvider consumers,
            BlockPos pos,
            BlockSequenceEntry seqEntry,
            TimelineClip clip,
            float elapsedTime
    ) {
        if (world == null || matrices == null || consumers == null || 
            pos == null || seqEntry == null || clip == null) {
            LOGGER.warn("BlockAnimationRenderer.renderBlock: 参数为空，跳过渲染");
            return;
        }
        
        ClipAnimationConfig config = clip.getAnimationConfig();
        if (config == null) {
            LOGGER.warn("BlockAnimationRenderer.renderBlock: 动画配置为空，跳过渲染");
            return;
        }
        
        // ================================
        // 1. 序列行为 → 映射到绝对时间点
        // ================================
        float normalizedSeqTime = seqEntry.getNormalizedTime(); // [0..1], 哪个方块先出现
        float clipStartTime = clip.getStartTime();
        float clipLength = clip.getLength();
        float seqStartTime = clipStartTime + clipLength * normalizedSeqTime;
        
        // 如果当前播放时间还没到这个方块的开始时间：不渲染
        if (elapsedTime < seqStartTime) {
            return; // 方块还未开始出现
        }
        
        // ================================
        // 2. 计算该方块的"局部动画时间"
        // ================================
        // localAnimTime: 方块在片段中的已播放时间（绝对秒数）
        float localAnimTime = elapsedTime - seqStartTime;
        
        // 确保 localAnimTime 在有效范围内
        if (localAnimTime < 0.0f) {
            return; // 方块还没开始，不渲染
        }
        
        float duration = config.getDuration();
        if (duration <= 0) {
            duration = 0.01f; // 避免除以零
        }
        
        // 动画进度：基于动画 duration
        float animProgress = localAnimTime / duration;
        if (animProgress > 1.0f) animProgress = 1.0f;
        if (animProgress < 0.0f) animProgress = 0.0f;
        
        // 应用缓动
        EasingFunction easing;
        try {
            easing = EasingFunction.fromString(config.getEasing());
        } catch (Exception e) {
            LOGGER.warn("无法解析缓动函数: {}, 使用 LINEAR", config.getEasing());
            easing = EasingFunction.LINEAR;
        }
        float easedT = easing.apply(animProgress);
        
        // 稳定随机（基于方块位置）
        Random rand = stableRandom(pos);
        
        matrices.push();
        
        // ================================
        // 3. 移动到方块世界位置并应用动画变换
        // ================================
        // 先移动到方块的世界坐标
        matrices.translate(pos.getX(), pos.getY(), pos.getZ());
        
        // 移动到方块中心（Minecraft 方块原点在左下角，需要偏移到中心）
        matrices.translate(0.5, 0.5, 0.5);
        
        IAnimationTransformer transformer = 
                AnimationTransformerFactory.getInstance().getTransformer(config.getMode());
        
        if (transformer != null) {
            try {
                // 获取变换结果
                TransformResult result = transformer.transform(
                        easedT,
                        config.getParams(),
                        rand
                );
                
                // 应用变换到 MatrixStack
                applyTransformResult(matrices, result);
                
            } catch (Exception e) {
                LOGGER.error("Transformer 错误: {}", e.getMessage(), e);
            }
        }
        
        // 移回方块原点（相对于方块位置）
        matrices.translate(-0.5, -0.5, -0.5);
        
        // ================================
        // 4. 执行特效（Effects）
        // ================================
        // 注意：特效渲染不修改 MatrixStack，只渲染装饰效果（粒子、光效等）
        for (EffectInstance effectInstance : config.getEffects()) {
            if (!effectInstance.isEnabled()) {
                continue;
            }
            
            IBlockEffect effect = BlockEffectRegistry.get(effectInstance.getType());
            if (effect == null) {
                continue;
            }
            
            // ---- 4.1 计算特效的局部时间 ----
            // 关键：特效时间基于"片段局部时间"（归一化），而不是动画 duration
            float clipLengthNorm = Math.max(clipLength, 0.0001f);
            float blockLocalNorm = localAnimTime / clipLengthNorm; // 方块在片段中的归一化进度 [0..1]
            
            // startOffset 和 duration 都是归一化的 [0..1]
            float effectStartOffset = effectInstance.getStartOffset();
            float effectDuration = effectInstance.getDuration();
            
            // 如果 duration <= 0，使用片段全长
            if (effectDuration <= 0.0f) {
                effectDuration = 1.0f;
            }
            
            float effectEnd = effectStartOffset + effectDuration;
            
            // 检查特效是否在激活时间范围内
            if (blockLocalNorm < effectStartOffset || blockLocalNorm > effectEnd) {
                continue; // 未到达或已经结束
            }
            
            // 计算特效的局部进度 [0..1]
            float effectT = (blockLocalNorm - effectStartOffset) / effectDuration;
            // 确保在 [0..1] 范围内
            if (effectT < 0.0f) effectT = 0.0f;
            if (effectT > 1.0f) effectT = 1.0f;
            
            // ---- 4.2 调用特效渲染 ----
            // 注意：这里传递的是特效的局部时间 t [0..1]，不是全局时间
            try {
                effect.render(world, pos, effectT, effectInstance, rand);
            } catch (Exception e) {
                LOGGER.error("BlockEffect 执行错误 type={}: {}", 
                             effectInstance.getType(), e.getMessage(), e);
            }
        }
        
        // ================================
        // 5. 渲染方块本体（调用 Minecraft block renderer）
        // ================================
        // 此时 MatrixStack 已经在方块位置，且应用了所有变换
        try {
            BlockRenderManager blockRenderManager = MinecraftClient.getInstance().getBlockRenderManager();
            if (blockRenderManager != null) {
                blockRenderManager.renderBlockAsEntity(
                        world.getBlockState(pos),
                        matrices,
                        consumers,
                        0xF000F0, // Light map (固定值，用于实体渲染)
                        0        // Overlay (0 = 无覆盖层)
                );
            }
        } catch (Exception e) {
            LOGGER.error("渲染方块本体失败: {}", e.getMessage(), e);
        }
        
        matrices.pop();
    }
    
    /**
     * 将 TransformResult 应用到 MatrixStack
     * 
     * <p>应用顺序：先缩放 → 再旋转 → 最后平移
     * 这是标准的 3D 变换顺序（TRS: Translate-Rotate-Scale 的逆序）
     */
    private static void applyTransformResult(MatrixStack matrices, TransformResult result) {
        if (result == null) {
            return;
        }
        
        // 1. 平移
        if (result.translateX != 0.0f || result.translateY != 0.0f || result.translateZ != 0.0f) {
            matrices.translate(result.translateX, result.translateY, result.translateZ);
        }
        
        // 2. 旋转（按 ZYX 顺序，这是 Minecraft 的标准旋转顺序）
        if (result.rotateZ != 0.0f) {
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(result.rotateZ));
        }
        if (result.rotateY != 0.0f) {
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(result.rotateY));
        }
        if (result.rotateX != 0.0f) {
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(result.rotateX));
        }
        
        // 3. 缩放
        if (result.scaleX != 1.0f || result.scaleY != 1.0f || result.scaleZ != 1.0f) {
            matrices.scale(result.scaleX, result.scaleY, result.scaleZ);
        }
        
        // 注意：透明度和颜色由渲染管线在后续阶段处理
        // 这里只处理几何变换（位移/旋转/缩放）
    }
}


package com.chronoblocks.core.animation;

import com.chronoblocks.core.animation.sequence.BlockSequenceEntry;
import com.chronoblocks.core.container.visibility.BlockVisualStateManager;
import com.chronoblocks.core.visual.BlockRenderMode;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import com.mojang.blaze3d.opengl.GlStateManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * BlockAnimationRenderer v3.1 - 重构版
 *
 * 渲染职责：
 *  1. 基于 BlockAnimationContext / BlockAnimationEngine 得到 BlockAnimationSnapshot
 *  2. 从 BlockVisualStateManager 查询覆盖可见性状态（遮挡透明、轨道隐藏等）
 *  3. 合并动画透明度 × 可见性透明度
 *  4. 将 snapshot 的“局部变换矩阵”叠加到世界矩阵上
 *  5. 使用自定义 RenderLayer 渲染半透明/ghost 方块
 *  6. 触发粒子等 BlockEffect
 *
 * 核心约定：
 *  - BlockAnimationSnapshot.transform 只包含局部矩阵（围绕方块中心），不含世界平移
 *  - 本 Renderer 负责 translate(pos) 再乘上 snapshot.transform
 *
 * @author ChronoBlocks Team
 * @version 3.1
 */
public class BlockAnimationRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlockAnimationRenderer.class);


    /**
     * 主入口：基于 Context 直接渲染一个动画方块
     * （这条路径一般用于“单方块调试 / 非时间线用例”，时间线走 RenderEntry 管线）
     */
    public static void renderBlock(
            @NotNull ClientWorld world,
            @NotNull MatrixStack matrices,
            @NotNull VertexConsumerProvider consumers,
            @NotNull BlockPos pos,
            @NotNull BlockSequenceEntry seqEntry,
            @NotNull TimelineClip clip,
            double globalTimeSec
    ) {
        // 性能敏感：renderBlock 可能在调试时被高频调用，避免 info 刷屏。
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[BlockAnimationRenderer] renderBlock called for pos={} time={}", pos, globalTimeSec);
        }
        try {
            // ========== Layer 1: 构建动画上下文 ==========
            ClipAnimationConfig config = clip.getAnimationConfig();
            if (config == null) {
                LOGGER.warn("BlockAnimationRenderer.renderBlock: animation config is null, skip render");
                return;
            }

            BlockAnimationContext ctx = BlockAnimationContext.build(
                    world, pos, clip, config, seqEntry, globalTimeSec
            );
            if (ctx == null) {
                // 动画还没开始，或者已经结束
                return;
            }

            // ========== Layer 2: 查询可见性状态 ==========
            BlockVisualStateManager visualStateManager = BlockVisualStateManager.getInstance();
            BlockVisualStateManager.BlockVisualState visualState = visualStateManager.getBlockState(pos);

            if (visualState.isHidden()) {
                return;
            }

            // ========== Layer 3: 采样动画快照 ==========
            BlockAnimationSnapshot snapshot = BlockAnimationEngine.sample(
                    ctx.getPos(),
                    world.getBlockState(ctx.getPos()),
                    ctx.getConfig(),
                    ctx.getSequenceEntry(),
                    ctx.getClip().getStartTime(),
                    ctx.getClip().getStartTime() + ctx.getClip().getLength(),
                    (float) ctx.getGlobalTimeSec(),
                    ctx.getSequenceEntry().getPos().asLong()
            );

            if (!snapshot.isVisible() || snapshot.getAlpha() <= 0.0f) {
                return;
            }

            // ========== Layer 4+5: 渲染本体（内部处理透明度合并） ==========
            renderAnimatedBlockWithVisualState(
                    matrices,
                    consumers,
                    world,
                    pos,
                    snapshot,
                    visualState,
                    world.getBlockState(pos)
            );

        } catch (Exception e) {
            LOGGER.error("渲染动画方块时出错: {}", e.getMessage(), e);
        }
    }

    /**
     * 向后兼容的旧版本方法（使用 float elapsedTime）
     *
     * @deprecated 请使用 renderBlock(world, matrices, consumers, pos, seqEntry, clip, double)
     */
    @Deprecated
    public static void renderBlock(
            @NotNull ClientWorld world,
            @NotNull MatrixStack matrices,
            @NotNull VertexConsumerProvider consumers,
            @NotNull BlockPos pos,
            @NotNull BlockSequenceEntry seqEntry,
            @NotNull TimelineClip clip,
            float elapsedTime
    ) {
        double globalTimeSec = clip.getStartTime() + elapsedTime;
        renderBlock(world, matrices, consumers, pos, seqEntry, clip, globalTimeSec);
    }

    /**
     * 批量渲染当前帧所有"需要覆盖层渲染"的条目（时间线主通路）
     * 
     * 🔥 修复：直接使用控制器传入的 snapshot，不再用世界时间重采样
     * - 时间线路径必须使用 TimelineClipBlockController 传入的 overrideSnapshot
     * - 避免用世界时间（world.getTime()）重采样导致动画跳到结束状态
     * - 确保使用时间线时间采样的正确动画状态
     */
    public static void renderEntries(
            @NotNull ClientWorld world,
            @NotNull MatrixStack matrices,
            @NotNull VertexConsumerProvider consumers,
            @NotNull Collection<BlockVisualStateManager.RenderEntry> entries
    ) {
        if (entries.isEmpty()) {
            return;
        }

        // 性能敏感：时间线渲染每帧都会调用，禁止 info 刷屏。
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[BlockAnimationRenderer] renderEntries called with {} entries", entries.size());
        }

        Vec3d cameraPos = MinecraftClient.getInstance().gameRenderer != null
                && MinecraftClient.getInstance().gameRenderer.getCamera() != null
                ? MinecraftClient.getInstance().gameRenderer.getCamera().getPos()
                : Vec3d.ZERO;
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        try {
            for (BlockVisualStateManager.RenderEntry entry : entries) {
                if (entry == null || entry.pos() == null) {
                    continue;
                }

                BlockPos pos = entry.pos();
                BlockState state = entry.blockState();
                BlockVisualStateManager.BlockVisualState visualState =
                        entry.visualState() != null ? entry.visualState() : BlockVisualStateManager.BlockVisualState.normal();

                BlockAnimationSnapshot snapshot = entry.overrideSnapshot();
                if (snapshot == null) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[BlockAnimationRenderer] renderEntries: missing snapshot for pos={} clip={}",
                                entry.pos(), entry.clip());
                    }
                    continue;
                }

                if (!snapshot.isVisible()) {
                    continue;
                }

                if (visualState.isHidden()) {
                    continue;
                }

                renderAnimatedBlockWithVisualState(
                        matrices,
                        consumers,
                        world,
                        pos,
                        snapshot,
                        visualState,
                        state
                );
            }
        } finally {
            matrices.pop();
        }
    }

    private static void renderAnimatedBlockWithVisualState(
            @NotNull MatrixStack matrices,
            @NotNull VertexConsumerProvider provider,
            @NotNull ClientWorld world,
            @NotNull BlockPos pos,
            @NotNull BlockAnimationSnapshot snapshot,
            @NotNull BlockVisualStateManager.BlockVisualState visualState,
            BlockState blockStateOverride
    ) {
        // 1. HIDDEN 模式：完全不渲染
        if (visualState.getMode() == BlockRenderMode.HIDDEN) {
            return;
        }

        // 1.1 NORMAL 且不需要抑制原版渲染：直接交给原版世界网格即可
        if (visualState.isNormal() && !visualState.shouldSuppressWorldRendering()) {
            return;
        }

        // 2. 计算最终 alpha（动画 × 可见性）
        float animAlpha = snapshot.getAlpha();
        if (animAlpha <= 0.0f) {
            return; // 动画还没开始/完全透明
        }

        float finalAlpha = animAlpha * visualState.getAlphaMultiplier();
        if (finalAlpha <= 0.01f) {
            return; // 最终透明度太低，不渲染
        }

        // 3. 深度写入策略
        boolean depthWrite = visualState.shouldWriteDepth();

        BlockState state = blockStateOverride != null ? blockStateOverride : world.getBlockState(pos);
        if (state == null) {
            return;
        }

        // 4. 应用世界平移 + 局部动画矩阵并渲染
        matrices.push();

        // 5.1 平移到方块世界坐标
        matrices.translate(pos.getX(), pos.getY(), pos.getZ());

        // 5.2 叠加局部动画矩阵（围绕方块中心）
        applyAnimationTransformFromSnapshot(matrices, snapshot);

        // 5.3 渲染方块（带 alpha 和深度控制）
        renderBlockWithAlphaAndDepth(matrices, provider, world, pos, state, finalAlpha, depthWrite);

        matrices.pop();
    }

    /**
     * 将 snapshot 的局部矩阵乘到当前 MatrixStack 顶部。
     *
     * 约定：
     *  - snapshot.transform 只包含局部动画变换（中心平移 / 旋转 / 缩放 / 微移）
     *  - 当前 MatrixStack 已经包含了世界矩阵（包括 translate(pos)）
     *
     * 结果：worldMatrix = worldMatrix * localAnimMatrix
     */
    private static void applyAnimationTransformFromSnapshot(MatrixStack matrices, BlockAnimationSnapshot snapshot) {
        if (snapshot == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[BlockAnimationRenderer] applyAnimationTransformFromSnapshot: snapshot is null");
            }
            return;
        }
        MatrixStack snapshotStack = snapshot.getTransform();
        if (snapshotStack == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[BlockAnimationRenderer] applyAnimationTransformFromSnapshot: snapshot.getTransform() is null for pos={}",
                        snapshot.getPos());
            }
            return;
        }

        // matrices.top = matrices.top * snapshot.top
        var snapshotMatrix = snapshotStack.peek().getPositionMatrix();
        var currentMatrix = matrices.peek().getPositionMatrix();
        
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("[BlockAnimationRenderer] applyAnimationTransformFromSnapshot: pos={} matrix={}",
                    snapshot.getPos(), snapshotMatrix);
        }
        
        currentMatrix.mul(snapshotMatrix);
    }

    // 特效触发已统一由 BlockAnimationEngine.sample() 内部负责：
    // - 时间线主通路（RenderEntry overrideSnapshot）也会走 sample() 采样并触发一次
    // - 避免在 Renderer 再额外派发造成双触发/刷屏

    /**
     * 底层工具：真正和 Minecraft 渲染管线打交道的地方
     */
    private static void renderBlockWithAlphaAndDepth(
            @NotNull MatrixStack matrices,
            @NotNull VertexConsumerProvider provider,
            @NotNull ClientWorld world,
            @NotNull BlockPos pos,
            @NotNull BlockState state,
            float alpha,
            boolean depthWrite
    ) {
        BlockRenderManager manager = MinecraftClient.getInstance().getBlockRenderManager();
        if (manager == null) {
            return;
        }

        try {
            GlStateManager._depthMask(depthWrite);

            // ✅ biome tint 修复（草/树叶等）：renderBlockAsEntity 会调用 BlockColors#getColor，
            // 但其 world/pos 可能为 null，导致使用默认颜色（看起来偏深/偏灰）。
            // 我们用 ThreadLocal 暂存 world+pos，让 BlockColorsMixin 在 world/pos 为空时回退到这里的上下文。
            com.chronoblocks.client.renderer.BlockColorRenderContext.push(world, pos);
            try {
                // 1.21.10+：使用 BlockRenderManager#renderBlockAsEntity（避免底层 model renderer 签名变化）
                manager.renderBlockAsEntity(
                        state,
                        matrices,
                        provider,
                        LightmapTextureManager.MAX_LIGHT_COORDINATE,
                        OverlayTexture.DEFAULT_UV
                );
            } finally {
                com.chronoblocks.client.renderer.BlockColorRenderContext.pop();
            }

        } catch (Exception e) {
            LOGGER.error("渲染方块失败: {}", e.getMessage(), e);
        } finally {
            GlStateManager._depthMask(true);
        }
    }

    @SuppressWarnings("unused")
    private static float clamp01(float v) {
        return v < 0f ? 0f : (Math.min(v, 1f));
    }
}
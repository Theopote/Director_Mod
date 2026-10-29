package com.director.mixin;

import com.director.client.DirectorClientController;
import com.director.client.RenderTimeState;
import com.director.client.world.WorldRenderOverride;
import com.director.timeline.playback.time.TimeSample;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.render.RenderTickCounter;
import org.joml.Matrix4f;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WorldRendererMixin v2.0
 * ============================================================
 * 在Director模式下：
 * - 完全取消Minecraft默认的方块高亮边框渲染
 * - 原版通过renderTargetBlockOutline()绘制的蓝色边框被拦截
 * - 配合DirectorInputSystem的自定义边框渲染
 * <p>
 * 注意：
 * - 方块渲染拦截已移至 BlockRenderManagerMixin
 * - 云颜色控制：Minecraft 1.21.4 中 WorldRenderer 没有 getCloudColor() 方法，
 *   云颜色控制暂时不可用。云量（cloudCoverage）可以通过天空颜色暗度（stormDarkness）
 *   和雾效果间接体现。
 * <p>
 * 基于Minecraft 1.21.4源码中的WorldRenderer类方法签名
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @org.spongepowered.asm.mixin.Unique private static final org.slf4j.Logger director$handoffLogger =
            org.slf4j.LoggerFactory.getLogger("DirectorSectionHandoff");
    @org.spongepowered.asm.mixin.Unique private long director$lastHandoffLog;
    @org.spongepowered.asm.mixin.Unique private int director$handoffRebuilds;
    @org.spongepowered.asm.mixin.Unique private double director$maxHandoffMs;
    @org.spongepowered.asm.mixin.Shadow private net.minecraft.client.render.BuiltChunkStorage chunks;
    @org.spongepowered.asm.mixin.Shadow private net.minecraft.client.render.chunk.ChunkBuilder chunkBuilder;
    @org.spongepowered.asm.mixin.Shadow private net.minecraft.client.world.ClientWorld world;

    @Inject(method = "setWorld(Lnet/minecraft/client/world/ClientWorld;)V", at = @At("HEAD"))
    private void director$resetReadiness(net.minecraft.client.world.ClientWorld nextWorld, CallbackInfo ci) {
        com.director.core.animation.StaticSectionMeshRenderer.release();
        com.director.core.container.visibility.ClientSectionInvalidation.getInstance().reset(nextWorld);
    }

    @Inject(method = "reload()V", at = @At("HEAD"))
    private void director$reloadReadiness(CallbackInfo ci) {
        com.director.core.animation.StaticSectionMeshRenderer.release();
        com.director.core.container.visibility.ClientSectionInvalidation.getInstance().reset(world);
    }

    @Inject(method = "scheduleChunkRender(IIIZ)V", at = @At("HEAD"))
    private void director$invalidateCachedLight(int x, int y, int z, boolean important, CallbackInfo ci) {
        com.director.core.animation.StaticSectionMeshRenderer.invalidate();
    }

    @Inject(method = "updateChunks", at = @At("HEAD"))
    private void director$flushWholeContainerHandoffs(net.minecraft.client.render.Camera camera, CallbackInfo ci) {
        if (world == null || chunks == null || chunkBuilder == null) return;
        var sections = com.director.core.animation.WholeContainerRenderSections.getInstance();
        if (sections.pendingCount(world) == 0) return;
        long started = System.nanoTime();
        var regions = new net.minecraft.client.render.chunk.ChunkRendererRegionBuilder();
        sections.drain(world, 2, System.nanoTime() + 2_000_000L, section -> {
            for (var chunk : chunks.chunks) {
                if (chunk == null || chunk.getSectionPos() != section) continue;
                chunkBuilder.rebuild(chunk, regions);
                chunk.cancelRebuild();
                director$handoffRebuilds++;
                return true;
            }
            // Nonresident sections have no old mesh; their next normal build reads current state.
            return true;
        });
        // Only this small batch must be ready before terrain drawing. The rest stays queued.
        chunkBuilder.upload();
        long now = System.nanoTime();
        director$maxHandoffMs = Math.max(director$maxHandoffMs, (now - started) / 1_000_000.0);
        if (now - director$lastHandoffLog >= 5_000_000_000L) {
            director$handoffLogger.info("[Perf][ContainerSections] rebuilt={} pending={} maxBatchMs={}",
                    director$handoffRebuilds, sections.pendingCount(world),
                    String.format(java.util.Locale.ROOT, "%.2f", director$maxHandoffMs));
            director$lastHandoffLog = now;
            director$handoffRebuilds = 0;
            director$maxHandoffMs = 0;
        }
    }


    /**
     * 🚫 拦截原版方块边框渲染
     * 在Director模式下完全取消原版蓝色边框
     * 基于Minecraft 1.21.4源码：使用renderTargetBlockOutline方法拦截
     */
    @Inject(method = "renderTargetBlockOutline", at = @At("HEAD"), cancellable = true)
    private void onRenderTargetBlockOutline(VertexConsumerProvider.Immediate vertexConsumers,
                                           MatrixStack matrices,
                                           boolean translucent,
                                           WorldRenderState renderState,
                                           CallbackInfo ci) {
        DirectorClientController controller = DirectorClientController.getInstance();
        if (controller != null && controller.shouldHideCrosshair()) {
            // 🚫 在Director模式下完全取消原版方块边框渲染
            ci.cancel();
        }
    }

    /**
     * 在 WorldRenderer.render 开始时同步渲染侧的 skyAngleRadians
     * 这样即使某些渲染路径直接根据 world/time 推导角度，也能从这里读取到覆盖值。
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void onWorldRenderHead(
            ObjectAllocator allocator,
            RenderTickCounter tickCounter,
            boolean renderBlockOutline,
            net.minecraft.client.render.Camera camera,
            Matrix4f projectionMatrix,
            Matrix4f positionMatrix,
            Matrix4f viewMatrix,
            GpuBufferSlice buffer,
            Vector4f frustum,
            boolean thickFog,
            CallbackInfo ci
    ) {
        WorldRenderOverride state = WorldRenderOverride.get();
        if (state.hasTimeOverride()) {
            TimeSample sample = state.timeSample();
            if (sample != null) {
                float rad = sample.getSkyAngle() * (float) (Math.PI * 2.0);
                RenderTimeState.setSkyAngleRadians(rad);
                return;
            }
        }
        RenderTimeState.clear();
    }

    // ⭐ v2.0：Weather - 云颜色控制
    // 
    // 注意：Minecraft 1.21.4 中 WorldRenderer 没有 getCloudColor() 方法。
    // 云的颜色可能通过其他方式控制（例如 CloudRenderer 或渲染管线中的其他方法）。
    // 如果需要实现云颜色控制，需要：
    // 1. 查找正确的云渲染方法（可能是 renderClouds 或类似方法）
    // 2. 或者通过 Shader 或其他渲染管线方式控制
    // 
    // 目前云颜色控制暂时不可用，但云量（cloudCoverage）可以通过其他视觉效果间接体现。
    // 例如：通过天空颜色暗度（stormDarkness）和雾效果来模拟云的影响。

}

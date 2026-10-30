package com.director.export.video;

import com.director.mixin.accessor.MinecraftClientAccessor;
import com.director.mixin.accessor.VideoWorldRendererAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.RenderTickCounter;

/** Reuses export-sized GPU targets and returns to the window target before HUD rendering. */
final class OffscreenVideoRenderer implements AutoCloseable {
    private Framebuffer target;
    private Framebuffer outline;

    void render(int width, int height, RenderTickCounter ticks, Runnable worldRender) {
        RenderSystem.assertOnRenderThread();
        MinecraftClient client = MinecraftClient.getInstance();
        MinecraftClientAccessor main = (MinecraftClientAccessor) client;
        VideoWorldRendererAccessor world = (VideoWorldRendererAccessor) client.worldRenderer;
        Framebuffer window = main.director$getFramebufferField();
        if (window == null || client.world == null) throw new IllegalStateException("离屏导出需要已加载的世界");
        ensureTargets(width, height, world.director$getEntityOutlineFramebuffer() != null);
        try (RenderStateScope state = new RenderStateScope()) {
            // Registered first so globals refresh last, after physical dimensions and targets are restored.
            state.restoreWith(() -> refreshGlobals(client, ticks));
            state.replace(main::director$getFramebufferField, main::director$setFramebufferField, target);
            if (world.director$getEntityOutlineFramebuffer() != null)
                state.replace(world::director$getEntityOutlineFramebuffer, world::director$setEntityOutlineFramebuffer, outline);
            ExportRenderDimensions.Scope dimensions = ExportRenderDimensions.enter(width, height);
            state.restoreWith(dimensions::close);
            if (client.getWindow().getFramebufferWidth() != width || client.getWindow().getFramebufferHeight() != height)
                throw new IllegalStateException("离屏尺寸钩子未生效，已停止导出以避免错误投影");
            refreshGlobals(client, ticks);
            worldRender.run();
            // Keep a live preview in the window; the captured frame still comes from the export target.
            if (window.getColorAttachmentView() != null) target.drawBlit(window.getColorAttachmentView());
        }
    }

    private void ensureTargets(int width, int height, boolean needsOutline) {
        if (target == null || target.textureWidth != width || target.textureHeight != height) {
            close();
            target = createTarget("Director Video Export", width, height);
        }
        if (needsOutline && outline == null)
            outline = createTarget("Director Video Export Outlines", width, height);
    }

    private static Framebuffer createTarget(String name, int width, int height) {
        Framebuffer framebuffer = new Framebuffer(name, true) {};
        try {
            framebuffer.resize(width, height);
            return framebuffer;
        } catch (RuntimeException | Error failure) {
            // The convenience constructor cannot release a partially allocated color/depth attachment.
            try { framebuffer.delete(); }
            catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static void refreshGlobals(MinecraftClient client, RenderTickCounter ticks) {
        client.gameRenderer.getGlobalSettings().set(Math.max(1, client.getWindow().getFramebufferWidth()),
                Math.max(1, client.getWindow().getFramebufferHeight()), client.options.getGlintStrength().getValue(),
                client.world == null ? 0 : client.world.getTime(), ticks, client.options.getMenuBackgroundBlurrinessValue());
    }

    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        // Clear references before deleting so failed deletion cannot leave a stale reusable target.
        Framebuffer oldTarget = target, oldOutline = outline;
        target = null; outline = null;
        try {
            if (oldOutline != null) oldOutline.delete();
        } finally {
            if (oldTarget != null) oldTarget.delete();
        }
    }
}

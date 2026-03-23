package com.director.mixin;

import com.director.ui.DirectorScreen;
import com.director.ui.imgui.ImGuiRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.tracy.TracyFrameCapturer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.x：将 ImGui 的最终 draw 放到 swapBuffers 前，避免被新渲染管线的 RenderPass/命令队列覆盖。
 */
@Mixin(RenderSystem.class)
public class RenderSystemMixin {

    @Inject(method = "flipFrame", at = @At("HEAD"))
    private static void director$beforeFlipFrame(Window window, TracyFrameCapturer capturer, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof DirectorScreen)) {
            return;
        }
        ImGuiRenderer renderer = ImGuiRenderer.getInstance();
        if (renderer.isInitialized() && renderer.hasPendingDrawData()) {
            renderer.renderPendingDrawData();
        }
    }
}



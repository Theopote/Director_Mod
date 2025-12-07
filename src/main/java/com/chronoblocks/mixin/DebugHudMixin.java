package com.chronoblocks.mixin;

import com.chronoblocks.client.ChronoBlocksClientController;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 🐛 DebugHudMixin - 隐藏调试信息
 * ==============================================
 * 在ChronoBlocks模式下隐藏：
 * - F3调试信息
 * - 性能图表
 * - 调试HUD
 * <p>
 * 基于Minecraft 1.21.4源码中的DebugHud类
 */
@Mixin(DebugHud.class)
public class DebugHudMixin {

    /**
     * 🚫 拦截调试HUD渲染
     * 在ChronoBlocks模式下完全隐藏调试信息
     * 基于Minecraft 1.21.4源码：render(DrawContext context)
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(DrawContext context, CallbackInfo ci) {
        ChronoBlocksClientController controller = ChronoBlocksClientController.getInstance();
        if (controller != null && controller.shouldHideCrosshair()) {
            ci.cancel(); // 🚫 不渲染调试信息
        }
    }
}

package com.chronoblocks.mixin;

import com.chronoblocks.client.ChronoBlocksClientController;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 十字星Mixin
 * <p>
 * 当ChronoBlocks窗口打开时，隐藏默认的十字星
 */
@Mixin(InGameHud.class)
public class CrosshairMixin {
    
    /**
     * 拦截十字星渲染
     * 当ChronoBlocks窗口打开时隐藏十字星
     */
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void onRenderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        ChronoBlocksClientController controller = ChronoBlocksClientController.getInstance();
        if (controller.shouldHideCrosshair()) {
            ci.cancel();
        }
    }
}
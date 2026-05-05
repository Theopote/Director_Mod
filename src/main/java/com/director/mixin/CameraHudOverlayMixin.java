package com.director.mixin;

import com.director.client.renderer.CameraHudOverlayRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders camera-related screen overlays in Minecraft HUD space.
 */
@Mixin(InGameHud.class)
public class CameraHudOverlayMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void director$renderCameraHudOverlays(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        CameraHudOverlayRenderer.render(context);
    }
}

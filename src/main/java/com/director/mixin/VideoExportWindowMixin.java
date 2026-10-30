package com.director.mixin;

import com.director.export.video.ExportRenderDimensions;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Window.class)
public class VideoExportWindowMixin {
    @Inject(method = "getFramebufferWidth", at = @At("HEAD"), cancellable = true)
    private void director$exportWidth(CallbackInfoReturnable<Integer> result) {
        var dimensions = ExportRenderDimensions.current();
        if (dimensions != null) result.setReturnValue(dimensions.width());
    }

    @Inject(method = "getFramebufferHeight", at = @At("HEAD"), cancellable = true)
    private void director$exportHeight(CallbackInfoReturnable<Integer> result) {
        var dimensions = ExportRenderDimensions.current();
        if (dimensions != null) result.setReturnValue(dimensions.height());
    }
}

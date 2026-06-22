package com.director.mixin;

import com.director.client.world.ClientWorldTintEffects;
import com.director.client.world.WorldRenderOverride;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FogRenderer mixin — 季节雾色委托 {@link ClientWorldTintEffects}。
 */
@Mixin(FogRenderer.class)
public abstract class BackgroundRendererMixin {

    @Inject(
        method = "applyFog(Lnet/minecraft/client/render/Camera;IZLnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void director$modifyFogColor(
            Camera camera,
            int viewDistance,
            boolean thickFog,
            RenderTickCounter tickCounter,
            float skyDarkness,
            ClientWorld world,
            CallbackInfoReturnable<Vector4f> cir
    ) {
        Vector4f original = cir.getReturnValue();
        if (original == null) {
            return;
        }

        WorldRenderOverride state = WorldRenderOverride.get();
        float[] sky = ClientWorldTintEffects.unpackRgb01(
                world.getSkyColor(camera.getPos(), tickCounter.getTickProgress(false)));
        Vector4f tinted = ClientWorldTintEffects.tintFogColor(
                original,
                state.seasonSample(),
                state.hasSeasonOverride(),
                sky[0],
                sky[1],
                sky[2]
        );
        if (tinted != null) {
            cir.setReturnValue(tinted);
        }
    }
}

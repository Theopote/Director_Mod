package com.director.mixin;

import com.director.bridge.DirectorActorIdHolder;
import com.director.bridge.DirectorActorScaleHolder;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies Director preview root scale inside LivingEntityRenderer's isolated matrix scope. */
@Mixin(LivingEntityRenderer.class)
public abstract class ActorScaleRendererMixin {
    @Inject(method = "scale(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;)V",
            at = @At("TAIL"))
    private void director$scalePreviewActor(LivingEntityRenderState state, MatrixStack matrices, CallbackInfo ci) {
        if (!(state instanceof DirectorActorIdHolder idHolder) || idHolder.director$getActorId() == null
                || !(state instanceof DirectorActorScaleHolder scaleHolder)) return;
        matrices.scale(scaleHolder.director$getActorScaleX(),
                scaleHolder.director$getActorScaleY(), scaleHolder.director$getActorScaleZ());
    }
}

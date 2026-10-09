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
    @org.spongepowered.asm.mixin.Unique
    private final java.util.Map<LivingEntityRenderState,org.joml.Vector3f> director$nativeScales=new java.util.WeakHashMap<>();
    @Inject(method="updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",at=@At("TAIL"))
    private void director$captureNativeScale(net.minecraft.entity.LivingEntity entity,LivingEntityRenderState state,float delta,CallbackInfo ci) {
        var previews=com.director.actor.preview.ActorPreviewManager.getInstance();
        if(previews.getActorId(entity)==null || entity instanceof com.director.actor.preview.PreviewActorEntity)
            director$nativeScales.remove(state);
        else director$nativeScales.put(state,previews.rootScale(entity));
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;scale(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;)V", shift = At.Shift.AFTER))
    private void director$scalePreviewActor(LivingEntityRenderState state, MatrixStack matrices,
            net.minecraft.client.render.command.OrderedRenderCommandQueue queue,
            net.minecraft.client.render.state.CameraRenderState camera, CallbackInfo ci) {
        var nativeScale=director$nativeScales.get(state);
        if(nativeScale!=null) { matrices.scale(nativeScale.x,nativeScale.y,nativeScale.z); return; }
        if (!(state instanceof DirectorActorIdHolder idHolder) || idHolder.director$getActorId() == null
                || !(state instanceof DirectorActorScaleHolder scaleHolder)) return;
        matrices.scale(scaleHolder.director$getActorScaleX(),
                scaleHolder.director$getActorScaleY(), scaleHolder.director$getActorScaleZ());
    }
}

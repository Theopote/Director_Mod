package com.director.mixin;

import com.director.actor.preview.NativeActorRenderPoses;
import com.director.actor.preview.NativeMobModelPose;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BipedEntityModel.class)
public abstract class NativeBipedPoseMixin {
    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V", at = @At("TAIL"))
    private void director$pose(BipedEntityRenderState state, CallbackInfo ci) {
        var pose = NativeActorRenderPoses.get(state);
        if (pose == null) return;
        BipedEntityModel<?> model = (BipedEntityModel<?>) (Object) this;
        NativeMobModelPose.applyBiped(pose, model.body, model.head, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg);
    }
}

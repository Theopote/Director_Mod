package com.director.mixin;

import com.director.actor.preview.NativeActorRenderPoses;
import com.director.actor.preview.NativeMobModelPose;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.QuadrupedEntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(QuadrupedEntityModel.class)
public abstract class NativeQuadrupedPoseMixin {
    @Shadow protected ModelPart head;
    @Shadow protected ModelPart body;
    @Shadow protected ModelPart rightHindLeg;
    @Shadow protected ModelPart leftHindLeg;
    @Shadow protected ModelPart rightFrontLeg;
    @Shadow protected ModelPart leftFrontLeg;

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;)V", at = @At("TAIL"))
    private void director$pose(LivingEntityRenderState state, CallbackInfo ci) {
        var pose = NativeActorRenderPoses.get(state);
        if (pose == null) return;
        NativeMobModelPose.applyQuadruped(pose, body, head, rightHindLeg, leftHindLeg, rightFrontLeg, leftFrontLeg);
    }
}

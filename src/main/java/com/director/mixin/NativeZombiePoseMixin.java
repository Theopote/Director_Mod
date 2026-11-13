package com.director.mixin;

import com.director.actor.preview.NativeActorRenderPoses;
import com.director.actor.preview.NativeMobModelPose;
import net.minecraft.client.render.entity.model.AbstractZombieModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.ZombieEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs after {@code ArmPosing.zombieArms}, which would otherwise replace the sampled arms. */
@Mixin(AbstractZombieModel.class)
public abstract class NativeZombiePoseMixin {
    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/ZombieEntityRenderState;)V", at = @At("TAIL"))
    private void director$pose(ZombieEntityRenderState state, CallbackInfo ci) {
        var pose = NativeActorRenderPoses.get(state);
        if (pose == null) return;
        BipedEntityModel<?> model = (BipedEntityModel<?>) (Object) this;
        NativeMobModelPose.applyBiped(pose, model.body, model.head, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg);
    }
}

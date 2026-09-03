package com.director.mixin;

import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.bridge.DirectorActorBoneScaleHolder;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.ArmorStandArmorEntityModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.ArmorStandEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies Director bone scale after vanilla has reset and posed the armor-stand model. */
@Mixin(ArmorStandArmorEntityModel.class)
public abstract class ArmorStandArmorModelMixin {
    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/ArmorStandEntityRenderState;)V",
            at = @At("TAIL"))
    private void director$applyPreviewBoneScale(ArmorStandEntityRenderState state, CallbackInfo ci) {
        if (!(state instanceof DirectorActorBoneScaleHolder holder)) return;
        BipedEntityModel<?> model = (BipedEntityModel<?>)(Object)this;
        apply(model.body, holder, HumanoidRig.BODY);
        apply(model.head, holder, HumanoidRig.HEAD);
        apply(model.hat, holder, HumanoidRig.HEAD);
        apply(model.leftArm, holder, HumanoidRig.LEFT_ARM);
        apply(model.rightArm, holder, HumanoidRig.RIGHT_ARM);
        apply(model.leftLeg, holder, HumanoidRig.LEFT_LEG);
        apply(model.rightLeg, holder, HumanoidRig.RIGHT_LEG);
    }

    private static void apply(ModelPart part, DirectorActorBoneScaleHolder holder, String boneId) {
        part.xScale = holder.director$getBoneScaleX(boneId);
        part.yScale = holder.director$getBoneScaleY(boneId);
        part.zScale = holder.director$getBoneScaleZ(boneId);
    }
}

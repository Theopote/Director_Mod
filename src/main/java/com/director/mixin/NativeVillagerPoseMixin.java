package com.director.mixin;

import com.director.actor.preview.NativeActorRenderPoses;
import com.director.actor.preview.NativeMobModelPose;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.EntityModelPartNames;
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.client.render.entity.state.VillagerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerResemblingModel.class)
public abstract class NativeVillagerPoseMixin {
    @Shadow private ModelPart head;
    @Shadow private ModelPart rightLeg;
    @Shadow private ModelPart leftLeg;
    @Shadow private ModelPart arms;

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/VillagerEntityRenderState;)V", at = @At("TAIL"))
    private void director$pose(VillagerEntityRenderState state, CallbackInfo ci) {
        var pose = NativeActorRenderPoses.get(state);
        if (pose == null) return;
        ModelPart body = ((VillagerResemblingModel) (Object) this).getRootPart().getChild(EntityModelPartNames.BODY);
        NativeMobModelPose.applyVillager(pose, body, head, arms, rightLeg, leftLeg);
    }
}

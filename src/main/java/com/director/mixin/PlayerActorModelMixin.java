package com.director.mixin;

import com.director.actor.preview.PlayerActorRenderPoses;
import com.director.actor.rig.skeleton.*;
import com.director.actor.preview.ArmorStandEulerAngles;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public abstract class PlayerActorModelMixin {
    @Inject(method="setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V",at=@At("TAIL"))
    private void director$applyPose(PlayerEntityRenderState state,CallbackInfo ci) {
        var pose=PlayerActorRenderPoses.get(state);
        if(pose==null || !HumanoidTopology.matches(pose.rig())) return;
        var model=(PlayerEntityModel)(Object)this;
        apply(model.body,pose,HumanoidRig.BODY);
        apply(model.head,pose,HumanoidRig.HEAD);
        apply(model.leftArm,pose,HumanoidRig.LEFT_ARM); apply(model.rightArm,pose,HumanoidRig.RIGHT_ARM);
        apply(model.leftLeg,pose,HumanoidRig.LEFT_LEG); apply(model.rightLeg,pose,HumanoidRig.RIGHT_LEG);
        copy(model.hat,model.head); copy(model.jacket,model.body);
        copy(model.leftSleeve,model.leftArm); copy(model.rightSleeve,model.rightArm);
        copy(model.leftPants,model.leftLeg); copy(model.rightPants,model.rightLeg);
    }
    private static void copy(ModelPart target,ModelPart source) {
        target.pitch=source.pitch; target.yaw=source.yaw; target.roll=source.roll;
        target.originX=source.originX; target.originY=source.originY; target.originZ=source.originZ;
        target.xScale=source.xScale; target.yScale=source.yScale; target.zScale=source.zScale;
    }
    private static void apply(ModelPart part,RigPose pose,String bone) {
        var transform=pose.localTransform(bone);
        var angles=ArmorStandEulerAngles.fromQuaternion(transform.rotation());
        part.pitch=angles.x; part.yaw=angles.y; part.roll=angles.z;
        var delta=transform.translation().sub(pose.rig().bone(pose.rig().requireBoneIndex(bone)).restLocal().translation());
        part.originX+=delta.x*16; part.originY-=delta.y*16; part.originZ+=delta.z*16;
        var scale=transform.scale(); part.xScale=scale.x; part.yScale=scale.y; part.zScale=scale.z;
    }
}

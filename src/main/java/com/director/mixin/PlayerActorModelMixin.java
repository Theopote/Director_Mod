package com.director.mixin;

import com.director.actor.preview.PlayerActorRenderPoses;
import com.director.actor.rig.skeleton.*;
import com.director.actor.preview.ArmorStandEulerAngles;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public abstract class PlayerActorModelMixin {
    @Inject(method="getTexturedModelData",at=@At("RETURN"))
    private static void director$jointGeometry(net.minecraft.client.model.Dilation dilation, boolean slim,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.client.model.ModelData> ci) {
        com.director.actor.preview.PlayerJointModel.build(ci.getReturnValue(),dilation,slim);
    }
    @Inject(method="setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V",at=@At("TAIL"))
    private void director$applyPose(PlayerEntityRenderState state,CallbackInfo ci) {
        var pose=PlayerActorRenderPoses.get(state);
        var model=(PlayerEntityModel)(Object)this;
        var joints=pose!=null && HumanoidTopology.matches(pose.rig())?pose:null;
        com.director.actor.preview.PlayerJointModel.apply(model.leftArm,joints,ArticulatedPlayerRig.LEFT_FOREARM);
        com.director.actor.preview.PlayerJointModel.apply(model.leftSleeve,joints,ArticulatedPlayerRig.LEFT_FOREARM);
        com.director.actor.preview.PlayerJointModel.apply(model.rightArm,joints,ArticulatedPlayerRig.RIGHT_FOREARM);
        com.director.actor.preview.PlayerJointModel.apply(model.rightSleeve,joints,ArticulatedPlayerRig.RIGHT_FOREARM);
        com.director.actor.preview.PlayerJointModel.apply(model.leftLeg,joints,ArticulatedPlayerRig.LEFT_SHIN);
        com.director.actor.preview.PlayerJointModel.apply(model.leftPants,joints,ArticulatedPlayerRig.LEFT_SHIN);
        com.director.actor.preview.PlayerJointModel.apply(model.rightLeg,joints,ArticulatedPlayerRig.RIGHT_SHIN);
        com.director.actor.preview.PlayerJointModel.apply(model.rightPants,joints,ArticulatedPlayerRig.RIGHT_SHIN);
        com.director.actor.preview.PlayerJointModel.torso(model.body,joints!=null);
        com.director.actor.preview.PlayerJointModel.torso(model.jacket,joints!=null);
        if(joints==null) return;
        apply(model.body,pose,HumanoidRig.BODY);
        apply(model.head,pose,HumanoidRig.HEAD);
        apply(model.leftArm,pose,HumanoidRig.LEFT_ARM); apply(model.rightArm,pose,HumanoidRig.RIGHT_ARM);
        apply(model.leftLeg,pose,HumanoidRig.LEFT_LEG); apply(model.rightLeg,pose,HumanoidRig.RIGHT_LEG);
        copy(model.hat,model.head); copy(model.jacket,model.body);
        copy(model.leftSleeve,model.leftArm); copy(model.rightSleeve,model.rightArm);
        copy(model.leftPants,model.leftLeg); copy(model.rightPants,model.rightLeg);
    }
    @Unique
    private static void copy(ModelPart target, ModelPart source) {
        // Clothing is a child of its limb and already inherits its transform.
        target.resetTransform();
    }
    @Inject(method="setArmAngle",at=@At("TAIL"))
    private void director$heldItemJoint(PlayerEntityRenderState state,net.minecraft.util.Arm arm,
            net.minecraft.client.util.math.MatrixStack matrices,CallbackInfo ci) {
        var pose=PlayerActorRenderPoses.get(state);
        if(pose==null) return;
        String id=arm==net.minecraft.util.Arm.LEFT?ArticulatedPlayerRig.LEFT_FOREARM:ArticulatedPlayerRig.RIGHT_FOREARM;
        int index=pose.rig().findBoneIndex(id);
        if(index<0 || !pose.isAuthored(index)) return;
        var value=pose.localTransform(index);
        matrices.translate(0,.25f,0);
        var bend=new LimbBendTransform(value.rotation(),value.scale());
        var offset=bend.lowerOffset().div(16);
        matrices.translate(offset.x,offset.y,offset.z);
        matrices.multiply(bend.rotation());
        var scale=value.scale(); matrices.scale(scale.x,scale.y,scale.z);
        matrices.translate(0,-.25f,0);
    }
    @Unique
    private static void apply(ModelPart part, RigPose pose, String bone) {
        var matrix=PlayerRenderWorldPose.modelTransforms(pose)[pose.rig().requireBoneIndex(bone)];
        var angles=ArmorStandEulerAngles.fromQuaternion(matrix.getUnnormalizedRotation(new org.joml.Quaternionf()));
        part.pitch=angles.x; part.yaw=angles.y; part.roll=angles.z;
        var position=matrix.getTranslation(new org.joml.Vector3f()).mul(16);
        part.originX=position.x; part.originY=position.y; part.originZ=position.z;
        var scale=matrix.getScale(new org.joml.Vector3f());
        part.xScale=scale.x; part.yScale=scale.y; part.zScale=scale.z;

    }
}

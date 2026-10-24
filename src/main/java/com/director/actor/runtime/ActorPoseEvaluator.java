package com.director.actor.runtime;

import com.director.actor.ActorType;
import com.director.actor.rig.*;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.playback.entity.EntitySample;
import java.util.*;

/** Shared pure composition/leg-constraint stage. All assets and editor drafts are explicit inputs. */
public final class ActorPoseEvaluator {
    private ActorPoseEvaluator() {}
    public record Layer(RigPose pose,PoseBlendSettings settings,float envelope) {
        public Layer { pose=Objects.requireNonNull(pose).copy();Objects.requireNonNull(settings);settings.effectiveWeight(envelope); }
        @Override public RigPose pose() {return pose.copy();}
    }
    public record Diagnostic(String stage,RigPoseComposer.Diagnostic detail) {}
    /** Pose is owned by the caller; layer inputs and samples are never modified. */
    public record Result(RigPose pose,Map<String,Float> authoredWeights,List<Diagnostic> diagnostics,Map<ActorPoseSources.Channel,String> sources) {
        public Result {authoredWeights=Map.copyOf(authoredWeights);diagnostics=List.copyOf(diagnostics);sources=Map.copyOf(sources);}
    }
    public static Result evaluate(ActorType type,ActorRigProfile profile,RigDefinition rig,Pose base,
                                  EntitySample sample,Layer preset,Layer manual,LimbBendMode bendMode) {
        RigPose pose=HumanoidTopology.matches(rig)?LegacyHumanoidPoseAdapter.convert(base,rig):new RigPose(rig);
        var sources=new ActorPoseSources();
        var before=ActorPoseSources.capture(pose);
        if(type==ActorType.PLAYER) {
            ActorRouteGait.applyJoints(pose,sample,profile,!(sample.poseOverrideEnabled && sample.poseOverrideArms),!(sample.poseOverrideEnabled && sample.poseOverrideLegs));
            sources.changed(before,pose,"基础步态／关节动作");
            before=ActorPoseSources.capture(pose);
            ActorRouteTurnPose.apply(pose,sample);
            sources.changed(before,pose,"路径端点转身／换脚");
        }
        var weights=new HashMap<String,Float>();var diagnostics=new ArrayList<Diagnostic>();
        pose=applyLayer(pose,preset,"preset-layer",weights,diagnostics);
        if(diagnostics.isEmpty()) sources.layer(preset,"动作预设");
        applyQuickPose(pose,sample);
        if(sample.poseOverrideEnabled) {
            var quick=new RigPose(rig);
            applyQuickPose(quick,sample);
            sources.layer(new Layer(quick,PoseBlendSettings.DEFAULT,1),"快速姿态");
        }
        int previousDiagnostics=diagnostics.size();
        pose=applyLayer(pose,manual,"pose-layer",weights,diagnostics);
        if(diagnostics.size()==previousDiagnostics) sources.layer(manual,sample.animationIsPreset?"动作预设":"手工骨骼关键帧");
        pose.setLimbBendMode(bendMode);
        before=ActorPoseSources.capture(pose);
        if(type==ActorType.PLAYER) ActorRouteFootContact.apply(pose,sample,profile,weights);
        sources.changed(before,pose,"路径脚底接触修正",true);
        return new Result(pose,weights,diagnostics,sources.snapshot());
    }
    private static RigPose applyLayer(RigPose base,Layer layer,String stage,Map<String,Float> weights,List<Diagnostic> diagnostics) {
        if(layer==null) return base;
        var result=RigPoseComposer.compose(base,List.of(layer.settings().layer(layer.pose,layer.envelope())));
        for(var diagnostic:result.diagnostics()) diagnostics.add(new Diagnostic(stage,diagnostic));
        if(result.diagnostics().isEmpty()) for(int i=0;i<layer.pose.rig().boneCount();i++)
            if(layer.settings().contributes(layer.pose,i,layer.envelope())
                    && (layer.pose.authoredChannels(i)&layer.settings().channels()&RigPose.ROTATION)!=0)
                weights.merge(layer.pose.rig().bone(i).id(),layer.settings().effectiveWeight(layer.envelope()),Math::max);
        return result.pose();
    }
    static void applyQuickPose(RigPose pose,EntitySample s) {
        if(!s.poseOverrideEnabled || !HumanoidTopology.matches(pose.rig())) return;
        if(s.poseOverrideBody) rotate(pose,HumanoidRig.BODY,s.poseBodyPitch,s.poseBodyYaw,s.poseBodyRoll);
        if(s.poseOverrideHead) rotate(pose,HumanoidRig.HEAD,s.poseHeadPitch,s.poseHeadYaw,0);
        if(s.poseOverrideArms) {
            rotate(pose,HumanoidRig.LEFT_ARM,s.poseLeftArmPitch,s.poseLeftArmYaw,s.poseLeftArmRoll);
            rotate(pose,HumanoidRig.RIGHT_ARM,s.poseRightArmPitch,s.poseRightArmYaw,s.poseRightArmRoll);
        }
        if(s.poseOverrideLegs) {
            rotate(pose,HumanoidRig.LEFT_LEG,s.poseLeftLegPitch,0,0);
            rotate(pose,HumanoidRig.RIGHT_LEG,s.poseRightLegPitch,0,0);
        }
    }
    private static void rotate(RigPose pose,String bone,float pitch,float yaw,float roll) {
        int index=pose.rig().requireBoneIndex(bone);
        pose.setLocalTransform(index,pose.localTransform(index).withRotation(new org.joml.Quaternionf().rotateXYZ(pitch,yaw,roll)),RigPose.ROTATION);
    }
}

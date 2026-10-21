package com.director.actor.rig.skeleton;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RigPoseComposerTest {
    private final RigDefinition rig=HumanoidRig.DEFINITION;
    private int index(String id) { return rig.requireBoneIndex(id); }
    private BitSet mask(String... ids) { var result=new BitSet();for(String id:ids) result.set(index(id));return result; }
    private RigPose blend(RigPose base,PoseLayer... layers) {
        var result=RigPoseComposer.compose(base,List.of(layers));assertTrue(result.diagnostics().isEmpty());return result.pose();
    }
    @Test void weightedOverrideUsesOnlyAuthoredAllowedBonesAndChannels() {
        var base=new RigPose(rig);var source=new RigPose(rig);int head=index(HumanoidRig.HEAD),leg=index(HumanoidRig.LEFT_LEG);
        var walk=new Quaternionf().rotateX(.7f);
        base.setLocalTransform(leg,base.localTransform(leg).withRotation(walk),RigPose.ROTATION);
        source.setLocalTransform(head,new BoneTransform(new Vector3f(10),new Quaternionf().rotateY(1),new Vector3f(3)),RigPose.ALL_CHANNELS);
        source.setLocalTransform(leg,source.localTransform(leg).withRotation(new Quaternionf().rotateZ(1)),RigPose.ROTATION);
        var layer=new PoseLayer(source,null,mask("head"),RigPose.SCALE,.5f,PoseLayer.BlendMode.OVERRIDE);
        var result=blend(base,layer);
        assertEquals(new Vector3f(2),result.localTransform(head).scale());
        assertEquals(base.localTransform(head).translation(),result.localTransform(head).translation());
        assertTrue(result.localTransform(head).rotation().equals(new Quaternionf(),1e-5f));
        assertTrue(result.localTransform(leg).rotation().equals(walk,1e-5f));
        assertEquals(RigPose.SCALE,result.authoredChannels(head));
    }
    @Test void fullWeightMatchesStrictOverlayAndRetainsActorAppearance() {
        var base=new RigPose(rig);base.setLimbBendMode(LimbBendMode.SHARP);var source=new RigPose(rig);
        source.setLocalTransform("head",source.localTransform("head").withRotation(new Quaternionf().rotateY(.9f)));
        var expected=base.overlay(source);var result=blend(base,PoseLayer.override(source,1));
        for(int i=0;i<rig.boneCount();i++) assertTrue(expected.localTransform(i).toMatrix().equals(result.localTransform(i).toMatrix(),1e-5f));
        assertEquals(LimbBendMode.SHARP,result.limbBendMode());
    }
    @Test void zeroWeightAndEmptyMasksDoNotClaimChannels() {
        var base=new RigPose(rig);var source=new RigPose(rig);source.setLocalTransform("head",BoneTransform.identity());
        var result=blend(base,PoseLayer.override(source,0),new PoseLayer(source,null,new BitSet(),7,1,PoseLayer.BlendMode.OVERRIDE));
        assertTrue(result.authoredBones().isEmpty());assertTrue(base.authoredBones().isEmpty());
        result=blend(base,new PoseLayer(source,null,mask("head"),0,1,PoseLayer.BlendMode.OVERRIDE));
        assertTrue(result.authoredBones().isEmpty());
    }
    @Test void layerSnapshotsCannotBeMutatedThroughInputsOrGetters() {
        var source=new RigPose(rig);var bones=mask("head");var q=new Quaternionf().rotateX(.5f);
        source.setLocalTransform("head",source.localTransform("head").withRotation(q));
        var layer=new PoseLayer(source,null,bones,2,1,PoseLayer.BlendMode.OVERRIDE);
        bones.clear();source.setLocalTransform("head",BoneTransform.identity());
        layer.boneMask().clear();layer.pose().setLocalTransform("head",BoneTransform.identity());
        assertTrue(blend(new RigPose(rig),layer).localTransform("head").rotation().equals(q,1e-5f));
    }
    @Test void additiveRestPoseDoesNotDoubleBindingTranslationOrScale() {
        var ref=new RigPose(rig);var source=ref.copy();var base=ref.copy();int head=index("head");
        source.setLocalTransform(head,source.localTransform(head));
        base.setLocalTransform(head,new BoneTransform(new Vector3f(1,2,3),new Quaternionf().rotateZ(.6f),new Vector3f(2)));
        var result=blend(base,PoseLayer.additive(source,ref,1));
        assertTrue(base.localTransform(head).toMatrix().equals(result.localTransform(head).toMatrix(),1e-5f));
    }
    @Test void additiveUsesReferenceDeltaLocalQuaternionOrderAndScaleRatio() {
        int head=index("head");var ref=new RigPose(rig);var source=new RigPose(rig);var base=new RigPose(rig);
        var referenceRotation=new Quaternionf().rotateX(.3f);var baseRotation=new Quaternionf().rotateY(.8f);
        ref.setLocalTransform(head,new BoneTransform(new Vector3f(3,4,5),referenceRotation,new Vector3f(2,4,8)));
        source.setLocalTransform(head,new BoneTransform(new Vector3f(5,8,11),new Quaternionf(referenceRotation).rotateZ(.6f),new Vector3f(4,8,16)));
        base.setLocalTransform(head,new BoneTransform(new Vector3f(10,20,30),baseRotation,new Vector3f(3)));
        var result=blend(base,PoseLayer.additive(source,ref,.5f)).localTransform(head);
        assertEquals(new Vector3f(11,22,33),result.translation());assertEquals(new Vector3f(4.5f),result.scale());
        assertTrue(result.rotation().equals(new Quaternionf(baseRotation).rotateZ(.3f),1e-5f));
        assertEquals(new Vector3f(3,4,5),ref.localTransform(head).translation());
    }
    @Test void quaternionOverrideUsesTheShortPathAcrossAngleWrap() {
        var base=new RigPose(rig);var source=new RigPose(rig);int head=index("head");
        base.setLocalTransform(head,base.localTransform(head).withRotation(new Quaternionf().rotateY((float)Math.toRadians(170))),2);
        source.setLocalTransform(head,source.localTransform(head).withRotation(new Quaternionf().rotateY((float)Math.toRadians(-170))),2);
        var actual=blend(base,PoseLayer.override(source,.5f)).localTransform(head).rotation().transform(new Vector3f(0,0,1));
        assertTrue(actual.equals(new Vector3f(0,0,-1),1e-5f));
    }
    @Test void incompatibleLayerIsDiagnosedAndLaterCompatibleLayersStillRun() {
        var base=new RigPose(rig);var bad=new RigPose(ArticulatedPlayerRig.DEFINITION);var good=new RigPose(rig);
        good.setLocalTransform("head",good.localTransform("head").withRotation(new Quaternionf().rotateX(.4f)));
        var result=RigPoseComposer.compose(base,List.of(PoseLayer.override(bad,1),PoseLayer.override(good,1)));
        assertEquals(1,result.diagnostics().size());assertEquals(0,result.diagnostics().getFirst().layerIndex());
        assertTrue(result.pose().localTransform("head").rotation().equals(good.localTransform("head").rotation(),1e-5f));
    }
    @Test void invalidAdditiveScaleRejectsTheWholeLayerBeforeWritingOtherBones() {
        var base=new RigPose(rig);var ref=new RigPose(rig);var source=new RigPose(rig);
        ref.setLocalTransform("head",new BoneTransform(new Vector3f(),new Quaternionf(),new Vector3f(0,1,1)));
        source.setLocalTransform("head",new BoneTransform(new Vector3f(10),new Quaternionf(),new Vector3f(2)));
        source.setLocalTransform("left_arm",source.localTransform("left_arm").withRotation(new Quaternionf().rotateX(1)));
        var result=RigPoseComposer.compose(base,List.of(PoseLayer.additive(source,ref,1)));
        assertEquals(1,result.diagnostics().size());assertTrue(result.pose().authoredBones().isEmpty());
    }
    @Test void overflowingAdditiveValuesCannotPartiallyApplyALayer() {
        var base=new RigPose(rig);var source=new RigPose(rig);
        source.setLocalTransform("head",source.localTransform("head").withRotation(new Quaternionf().rotateX(.5f)));
        var huge=new BoneTransform(new Vector3f(Float.MAX_VALUE,0,0),new Quaternionf(),new Vector3f(1));
        base.setLocalTransform("left_arm",huge);source.setLocalTransform("left_arm",huge);
        var result=RigPoseComposer.compose(base,List.of(PoseLayer.additive(source,null,1)));
        assertEquals(1,result.diagnostics().size());assertFalse(result.pose().isAuthored(index("head")));
        assertEquals(base.localTransform("left_arm").translation(),result.pose().localTransform("left_arm").translation());
    }
    @Test void nonFiniteWeightAndUnknownMaskAreRejected() {
        var pose=new RigPose(rig);
        assertThrows(IllegalArgumentException.class,()->PoseLayer.override(pose,Float.NaN));
        var bad=new BitSet();bad.set(100);
        assertThrows(IllegalArgumentException.class,()->new PoseLayer(pose,null,bad,1,1,PoseLayer.BlendMode.OVERRIDE));
        assertThrows(IllegalArgumentException.class,()->new PoseLayer(pose,null,mask("head"),8,1,PoseLayer.BlendMode.OVERRIDE));
    }
    @Test void layerOrderIsExplicitAndRepeatedSamplingDoesNotAccumulate() {
        var base=new RigPose(rig);var a=new RigPose(rig);var b=new RigPose(rig);
        a.setLocalTransform(index("head"),new BoneTransform(new Vector3f(10),new Quaternionf(),new Vector3f(1)),RigPose.TRANSLATION);
        b.setLocalTransform(index("head"),new BoneTransform(new Vector3f(20),new Quaternionf(),new Vector3f(1)),RigPose.TRANSLATION);
        base.setLocalTransform(index("head"),BoneTransform.identity(),RigPose.TRANSLATION);
        var layers=List.of(PoseLayer.override(a,.5f),PoseLayer.override(b,.5f));
        for(int repeat=0;repeat<3;repeat++)
            assertEquals(new Vector3f(12.5f),RigPoseComposer.compose(base,layers).pose().localTransform("head").translation());
        assertEquals(new Vector3f(),base.localTransform("head").translation());
        assertEquals(new Vector3f(10),blend(base,layers.get(1),layers.get(0)).localTransform("head").translation());
    }
}

package com.director.actor.rig.skeleton;

import org.junit.jupiter.api.Test;
import org.joml.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PoseBlendSettingsTest {
    @Test void boneIdsResolvePerRigAndEmptySelectionMeansNone() {
        var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);
        var ids=new HashSet<>(Set.of(HumanoidRig.HEAD,"missing"));
        var settings=new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,.4f,RigPose.ROTATION,false,ids);
        ids.clear();
        assertEquals(Set.of(HumanoidRig.HEAD,"missing"),settings.boneIds());
        assertEquals(1,settings.layer(pose).boneMask().cardinality());
        assertTrue(new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,1,7,false,Set.of()).layer(pose).boneMask().isEmpty());
        assertEquals(pose.rig().boneCount(),PoseBlendSettings.DEFAULT.layer(pose).boneMask().cardinality());
    }
    @Test void contributionRequiresNonzeroWeightAndSelectedAuthoredChannel() {
        var pose=new RigPose(HumanoidRig.DEFINITION);int head=pose.rig().requireBoneIndex(HumanoidRig.HEAD);
        pose.setLocalTransform(head,pose.localTransform(head).withRotation(new Quaternionf().rotateY(.4f)),RigPose.ROTATION);
        assertTrue(PoseBlendSettings.DEFAULT.contributes(pose,head));
        assertFalse(new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,0,7,true,Set.of()).contributes(pose,head));
        assertFalse(new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,1,RigPose.SCALE,true,Set.of()).contributes(pose,head));
        assertFalse(new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,1,7,false,Set.of()).contributes(pose,head));
    }
    @Test void invalidPersistentValuesAreRejectedBeforeRuntime() {
        for(float w:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1,2})
            assertThrows(IllegalArgumentException.class,()->new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,w,7,true,Set.of()));
        assertThrows(IllegalArgumentException.class,()->new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,1,8,true,Set.of()));
    }
    private PoseBlendSettings fades(float in,float out) {
        return new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,.8f,7,true,Set.of(),in,out);
    }
    @Test void fadesUseSmoothClipSecondsAndKeepNominalWeightSeparate() {
        var settings=fades(2,1);
        assertEquals(0,settings.envelopeAt(0,6));
        assertEquals(.15625f,settings.envelopeAt(.5f,6),1e-6f);
        assertEquals(.5f,settings.envelopeAt(1,6),1e-6f);
        assertEquals(1,settings.envelopeAt(2,6));
        assertEquals(1,settings.envelopeAt(5,6));
        assertEquals(.5f,settings.envelopeAt(5.5f,6),1e-6f);
        assertEquals(0,settings.envelopeAt(6,6));
        assertEquals(.4f,settings.effectiveWeight(settings.envelopeAt(1,6)),1e-6f);
        assertEquals(.8f,settings.weight());
    }
    @Test void oversizedFadesCompressProportionallyWithoutNaNsOrLosingPeak() {
        var settings=fades(8,4);
        assertEquals(0,settings.envelopeAt(0,3));
        assertEquals(.5f,settings.envelopeAt(1,3),1e-6f);
        assertEquals(1,settings.envelopeAt(2,3),1e-6f);
        assertEquals(.5f,settings.envelopeAt(2.5f,3),1e-6f);
        assertEquals(0,settings.envelopeAt(3,3));
        settings=fades(Float.MAX_VALUE,Float.MAX_VALUE);
        assertEquals(1,settings.envelopeAt(.5f,1),1e-6f);
        assertEquals(1,fades(.00001f,.00001f).envelopeAt(.00001f,.00002f),1e-6f);
    }
    @Test void singleSidedZeroAndOutOfRangeFadesHaveDefinedBoundaries() {
        assertEquals(1,fades(0,0).envelopeAt(0,2));
        assertEquals(1,fades(0,0).envelopeAt(2,2));
        assertEquals(1,fades(0,1).envelopeAt(0,2));
        assertEquals(1,fades(1,0).envelopeAt(2,2));
        assertEquals(0,fades(0,0).envelopeAt(-1,2));
        assertEquals(0,fades(0,0).envelopeAt(3,2));
        assertEquals(0,fades(1,1).envelopeAt(0,0));
        assertEquals(0,fades(1,1).envelopeAt(Float.NaN,2));
        assertEquals(0,fades(1,1).envelopeAt(1,Float.POSITIVE_INFINITY));
    }
    @Test void invalidFadeDurationsAndResolvedEnvelopesAreRejected() {
        for(float invalid:new float[]{-.1f,Float.NaN,Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,()->fades(invalid,0));
            assertThrows(IllegalArgumentException.class,()->fades(0,invalid));
            assertThrows(IllegalArgumentException.class,()->fades(0,0).effectiveWeight(invalid));
        }
        assertThrows(IllegalArgumentException.class,()->fades(0,0).effectiveWeight(1.1f));
    }
    @Test void invalidBoneIdSetsAreRejectedAtTheEditingBoundary() {
        assertThrows(IllegalArgumentException.class,()->new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,1,7,false,null));
        var nullId=new HashSet<String>();nullId.add(null);
        assertThrows(IllegalArgumentException.class,()->new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,1,7,false,nullId));
        assertThrows(IllegalArgumentException.class,()->new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,1,7,false,Set.of(" ")));
    }
}

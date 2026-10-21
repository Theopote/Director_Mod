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
}

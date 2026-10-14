package com.director.ui.actor.pose;

import com.director.actor.rig.skeleton.*;
import org.joml.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ActorPosePresetsTest {
    @Test void scopedPresetPreservesOtherBonesTranslationAndScale() {
        RigPose source = new RigPose(HumanoidRig.DEFINITION);
        source.setLocalTransform(HumanoidRig.RIGHT_ARM, new BoneTransform(new Vector3f(1,2,3),
                new Quaternionf(), new Vector3f(2)));
        RigPose result = ActorPosePresets.apply(source, ActorPosePresets.Preset.RAISE_HAND, List.of(HumanoidRig.RIGHT_ARM));
        assertEquals(new Vector3f(1,2,3), result.localTransform(HumanoidRig.RIGHT_ARM).translation());
        assertEquals(new Vector3f(2), result.localTransform(HumanoidRig.RIGHT_ARM).scale());
        assertFalse(result.localTransform(HumanoidRig.RIGHT_ARM).rotation().equals(new Quaternionf(), 1e-5f));
        assertTrue(result.localTransform(HumanoidRig.LEFT_ARM).rotation().equals(new Quaternionf(), 1e-5f));
        assertTrue(source.localTransform(HumanoidRig.RIGHT_ARM).rotation().equals(new Quaternionf(), 1e-5f));
    }
    @Test void neutralRestoresRotationWithoutResettingScaleAndRootIsUntouched() {
        RigPose source = new RigPose(HumanoidRig.DEFINITION);
        source.setLocalTransform(HumanoidRig.HEAD, new BoneTransform(new Vector3f(), new Quaternionf().rotateX(1),new Vector3f(3)));
        RigPose result = ActorPosePresets.apply(source, ActorPosePresets.Preset.NEUTRAL,List.of(HumanoidRig.HEAD,HumanoidRig.ROOT));
        assertTrue(result.localTransform(HumanoidRig.HEAD).rotation().equals(new Quaternionf(),1e-5f));
        assertEquals(3f,result.localTransform(HumanoidRig.HEAD).scale().x);
        assertFalse(result.isAuthored(HumanoidRig.DEFINITION.requireBoneIndex(HumanoidRig.ROOT)));
    }
}

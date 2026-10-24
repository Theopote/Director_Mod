package com.director.ui.properties.actor;

import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.entity.ActorPresetBinding;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorPresetScopeTest {
    private ActorPresetBinding binding() {
        return new ActorPresetBinding("wave",false,1.5f,new PoseBlendSettings(PoseLayer.BlendMode.ADDITIVE,.6f,6,true,Set.of(),.2f,.4f));
    }
    @Test void upperScopeIncludesElbowsButNeverLegsOrRootAndRetainsPlaybackSettings() {
        var before=binding();var next=ActorPresetScope.UPPER.apply(before,ArticulatedPlayerRig.DEFINITION);
        assertTrue(next.blend().boneIds().containsAll(Set.of("body","head","left_arm","right_arm","left_forearm","right_forearm")));
        assertFalse(next.blend().boneIds().stream().anyMatch(ActorPresetScope::isLeg));
        assertFalse(next.blend().boneIds().contains("root"));assertFalse(next.blend().allBones());
        assertEquals(before.animationId(),next.animationId());assertEquals(before.loop(),next.loop());assertEquals(before.speed(),next.speed());
        assertEquals(before.blend().mode(),next.blend().mode());assertEquals(before.blend().channels(),next.blend().channels());
        assertEquals(before.blend().weight(),next.blend().weight());assertEquals(before.blend().fadeIn(),next.blend().fadeIn());assertEquals(before.blend().fadeOut(),next.blend().fadeOut());
        assertTrue(before.blend().allBones());
        assertEquals("仅上半身",ActorPresetScope.currentLabel(next,ArticulatedPlayerRig.DEFINITION));
    }
    @Test void lowerScopeHandlesRigidAndArticulatedLegsAndFullClearsOldMask() {
        var lower=ActorPresetScope.LOWER.apply(binding(),ArticulatedPlayerRig.DEFINITION);
        assertEquals(Set.of("left_leg","right_leg","left_shin","right_shin"),lower.blend().boneIds());
        assertEquals(Set.of("left_leg","right_leg"),ActorPresetScope.LOWER.apply(binding(),HumanoidRig.DEFINITION).blend().boneIds());
        var full=ActorPresetScope.FULL.apply(lower,ArticulatedPlayerRig.DEFINITION);
        assertTrue(full.blend().allBones());assertTrue(full.blend().boneIds().isEmpty());
        assertEquals("全身",ActorPresetScope.currentLabel(full,ArticulatedPlayerRig.DEFINITION));
    }
    @Test void unfamiliarSkeletonSupportsFullButNotHumanoidGuesswork() {
        var rig=new RigDefinition("test:single","Single",List.of(HumanoidRig.DEFINITION.bone(0)));
        assertTrue(ActorPresetScope.FULL.supported(rig));assertFalse(ActorPresetScope.UPPER.supported(rig));
        assertThrows(IllegalArgumentException.class,()->ActorPresetScope.LOWER.apply(binding(),rig));
    }
}

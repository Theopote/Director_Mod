package com.director.ui.properties.actor;

import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.entity.ActorPresetBinding;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ActorPresetFeedbackTest {
    private ActorPresetBinding binding(String id,int channels,Set<String> bones) {
        return new ActorPresetBinding(id,false,1,new PoseBlendSettings(PoseLayer.BlendMode.OVERRIDE,.8f,
                channels,false,bones,2,1));
    }
    @Test void feedbackIntersectsActualOwnershipAndSelectedChannels() {
        var rig=ArticulatedPlayerRig.DEFINITION;
        for(var binding:new ActorPresetBinding[]{binding("wave",1,Set.of("right_arm")),
                binding("wave",2,Set.of("left_leg")),binding("wave",0,Set.of("right_arm"))}) {
            var feedback=ActorPresetFeedback.resolve(binding,rig,3,6);
            assertTrue(feedback.available());assertTrue(feedback.bones().isEmpty());assertEquals(0,feedback.weight());
        }
        var valid=ActorPresetFeedback.resolve(binding("wave",2,Set.of("right_arm","left_leg")),rig,1,6);
        assertEquals(.4f,valid.weight(),1e-6f);assertEquals(java.util.List.of("right_arm"),valid.bones());
    }
    @Test void missingOutsideRangeAndFadeFeedbackRemainHonest() {
        var rig=ArticulatedPlayerRig.DEFINITION;
        assertFalse(ActorPresetFeedback.resolve(binding("test:missing",2,Set.of("head")),rig,1,6).available());
        assertEquals(0,ActorPresetFeedback.resolve(binding("wave",2,Set.of("right_arm")),rig,-1,6).weight());
        assertEquals(0,ActorPresetFeedback.resolve(binding("wave",2,Set.of("right_arm")),rig,7,6).weight());
        assertEquals(.8f,ActorPresetFeedback.resolve(binding("wave",2,Set.of("right_arm")),rig,3,6).weight(),1e-6f);
    }
}

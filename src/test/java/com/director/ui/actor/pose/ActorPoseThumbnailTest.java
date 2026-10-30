package com.director.ui.actor.pose;

import com.director.actor.rig.skeleton.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ActorPoseThumbnailTest {
    @Test void projectionUsesPoseHierarchyAndDoesNotMutateItsInput() {
        var rest=new RigPose(ArticulatedPlayerRig.DEFINITION);
        var pose=ActorPosePresets.apply(rest,ActorPosePresets.Preset.RAISE_HAND,List.of(HumanoidRig.RIGHT_ARM));
        var original=pose.localTransform(HumanoidRig.RIGHT_ARM);
        var lines=ActorPoseThumbnail.project(pose);
        assertFalse(lines.isEmpty());assertNotEquals(ActorPoseThumbnail.project(rest),lines);
        for(var s:lines) for(float v:new float[]{s.x1(),s.y1(),s.x2(),s.y2()}) assertTrue(Float.isFinite(v) && v>=0 && v<=1);
        assertEquals(original,pose.localTransform(HumanoidRig.RIGHT_ARM));
    }
}

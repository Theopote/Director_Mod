package com.director.actor.runtime;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ActorPoseFeedbackTest {
    @Test void missingTargetPreservesManualSourceAndPartialGazeRetainsLowerLayer() {
        var feedback=new ActorPoseFeedback(2,7,Map.of(new ActorPoseSources.Channel("head",2),"手工骨骼关键帧"),List.of(),"等待求解",false,0);
        assertEquals("手工骨骼关键帧",feedback.withGaze("目标不存在",false,0).source("head",2));
        var partial=feedback.withGaze("已生效",true,.5f);
        assertTrue(partial.source("head",2).contains("50%"));
        assertTrue(partial.source("head",2).contains("手工骨骼关键帧"));
        assertEquals("注视约束",feedback.withGaze("已生效",true,1).source("head",2));
        assertFalse(partial.source("head",4).contains("注视"));
        assertFalse(partial.source("body",2).contains("注视"));
    }
    @Test void snapshotIsImmutableAndRejectsOtherTimeOrRevision() {
        var channels=new HashMap<ActorPoseSources.Channel,String>();var diagnostics=new ArrayList<String>();diagnostics.add("rejected");
        var feedback=new ActorPoseFeedback(2,7,channels,diagnostics,"未启用",false,0);
        channels.put(new ActorPoseSources.Channel("head",2),"stale");diagnostics.clear();
        assertTrue(feedback.sources().isEmpty());assertEquals(1,feedback.diagnostics().size());
        assertTrue(feedback.matches(2,7));assertFalse(feedback.matches(3,7));assertFalse(feedback.matches(2,8));
    }
}

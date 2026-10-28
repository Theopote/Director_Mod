package com.director.actor.runtime;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ActorPoseFeedbackTest {
    @Test void standardStageDetailsAreCodesAndEveryCodeHasBothUiTranslations() throws Exception {
        var absent=ActorPoseStage.layer(ActorPoseStage.Kind.PRESET,null,
                com.director.actor.rig.skeleton.PoseBlendSettings.DEFAULT,0,null);
        assertEquals(ActorPoseStage.Detail.ABSENT,absent.detailCode());assertEquals("",absent.detail());
        for(String language:List.of("en_us","zh_cn")) {
            try(var stream=getClass().getResourceAsStream("/assets/director/lang/"+language+".json")) {
                assertNotNull(stream);
                var json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                assertTrue(String.format(Locale.ROOT,json.get("gui.director.actor.coordination.layer_order").getAsString()).contains("100%"));
                for(var code:ActorPoseStage.Detail.values()) if(code!=ActorPoseStage.Detail.DIAGNOSTIC)
                    assertTrue(json.has("gui.director.actor.coordination.detail."+code.name().toLowerCase(Locale.ROOT)),code.name());
                for(var state:ActorPoseStage.State.values())
                    assertTrue(json.has("gui.director.actor.coordination.state."+state.name().toLowerCase(Locale.ROOT)),state.name());
            }
        }
    }
    @Test void motionKeepsSemanticEnumsInsteadOfPreformattedUiText() {
        var sample=new com.director.timeline.playback.entity.EntitySample();
        sample.action=com.director.actor.action.ActorActionType.WALK;
        sample.routeClip=new com.director.timeline.model.entity.ActorRouteClip("Route",0,4);
        sample.routeClip.setLoopMode(com.director.timeline.model.entity.ActorRouteClip.LoopMode.PING_PONG);
        var motion=ActorCoordinationFeedback.Motion.capture(sample);
        assertEquals(com.director.actor.action.ActorActionType.WALK,motion.action());
        assertEquals(com.director.timeline.model.entity.ActorRouteClip.LoopMode.PING_PONG,motion.loop());
    }
    @Test void coordinationSnapshotSurvivesGazeUpdateWithoutFollowingMutableRouteEdits() {
        var sample=new com.director.timeline.playback.entity.EntitySample();sample.speed=2;
        sample.routeClip=new com.director.timeline.model.entity.ActorRouteClip("Before",0,4);
        var stages=new ArrayList<ActorPoseStage>();stages.add(ActorPoseStage.status(ActorPoseStage.Kind.MANUAL,ActorPoseStage.State.ABSENT,"None"));
        var coordination=new ActorCoordinationFeedback(ActorCoordinationFeedback.Motion.capture(sample),"test:preset",true,stages);
        var feedback=new ActorPoseFeedback(2,7,Map.of(),List.of(),"等待",false,0,coordination);
        stages.clear();sample.routeClip.setName("After");sample.speed=8;
        var updated=feedback.withGaze("已生效",true,.5f);
        assertSame(coordination,updated.coordination());assertEquals("Before",updated.coordination().motion().routeName());
        assertEquals(2,updated.coordination().motion().speed());assertEquals(1,updated.coordination().stages().size());
        assertFalse(updated.matches(3,7));
    }

    @Test void missingTargetPreservesManualSourceAndPartialGazeRetainsLowerLayer() {
        var feedback=new ActorPoseFeedback(2,7,Map.of(new ActorPoseSources.Channel("head",2),"手工骨骼关键帧"),List.of(),"等待求解",false,0,null);
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
        var feedback=new ActorPoseFeedback(2,7,channels,diagnostics,"未启用",false,0,null);
        channels.put(new ActorPoseSources.Channel("head",2),"stale");diagnostics.clear();
        assertTrue(feedback.sources().isEmpty());assertEquals(1,feedback.diagnostics().size());
        assertTrue(feedback.matches(2,7));assertFalse(feedback.matches(3,7));assertFalse(feedback.matches(2,8));
    }
}

package com.director.actor.prop;
import com.director.timeline.model.entity.*;
import com.director.timeline.playback.entity.EntityActionEvent;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorPropTimelineSamplerTest {
    ActorTrack track=new ActorTrack();
    EntityActionClip event(float time,EntityActionClip.ActionType type,Map<String,Object> params) {
        var c=new EntityActionClip("Prop",time,time+.1f); c.setActionType(type); c.getParams().putAll(params); track.addClip(c); return c;
    }
    EntityActionClip attach(float at,String prop) { return event(at,EntityActionClip.ActionType.ATTACH_PROP,Map.of("slot","MAIN_HAND","propId",prop)); }
    ActorPropTimelineSampler.Snapshot sample(float t) { return ActorPropTimelineSampler.sample(track,t,at->new Vec3d(at,2,0)); }
    @Test void seekRewindAndLoopReconstructSameHeldState() {
        attach(1,"stone"); event(3,EntityActionClip.ActionType.DETACH_PROP,Map.of("slot","MAIN_HAND"));
        assertTrue(sample(4).held().isEmpty()); assertEquals("stone",sample(2).held().get(ActorPropSlot.MAIN_HAND).propId());
        assertTrue(sample(0).held().isEmpty()); assertEquals(sample(2),sample(2)); assertTrue(sample(4).held().isEmpty());
    }
    @Test void pausedEditsMuteAndRemovalAreReflectedImmediately() {
        var c=attach(1,"stone"); assertFalse(sample(2).held().isEmpty()); c.setParam("propId","dirt");
        assertEquals("dirt",sample(2).held().get(ActorPropSlot.MAIN_HAND).propId()); c.setMuted(true); assertTrue(sample(2).held().isEmpty());
        c.setMuted(false); track.removeClip(c.getId()); assertTrue(sample(2).held().isEmpty());
    }
    @Test void malformedSwitchCannotRemoveExistingProp() {
        attach(1,"stone"); event(2,EntityActionClip.ActionType.SWITCH_PROP,Map.of("oldSlot","MAIN_HAND","newSlot","OFF_HAND","newPropId","missing"));
        assertEquals("stone",sample(4).held().get(ActorPropSlot.MAIN_HAND).propId());
    }
    @Test void equalTimeEventsUsePersistedOrderAndSwitchIsAtomic() {
        attach(1,"stone"); event(1,EntityActionClip.ActionType.SWITCH_PROP,Map.of("oldSlot","MAIN_HAND","newSlot","OFF_HAND","newPropId","dirt"));
        assertFalse(sample(2).held().containsKey(ActorPropSlot.MAIN_HAND)); assertEquals("dirt",sample(2).held().get(ActorPropSlot.OFF_HAND).propId());
    }
    void throwAt(float t) { event(t,EntityActionClip.ActionType.THROW_PROP,Map.of("slot","MAIN_HAND","targetX",6,"targetY",2,"targetZ",0,"flightTime",2,"arcHeight",1)); }
    @Test void flightIsAbsoluteTimeIncludingDirectSeekAndRewind() {
        attach(1,"stone"); throwAt(2);
        assertTrue(sample(3).held().isEmpty()); assertEquals(new Vec3d(4,3,0),sample(3).flights().getFirst().position());
        assertTrue(sample(4).flights().isEmpty()); assertEquals(new Vec3d(4,3,0),sample(3).flights().getFirst().position());
        assertFalse(sample(1.5f).held().isEmpty()); assertEquals(new Vec3d(2,2,0),sample(2).flights().getFirst().position());
    }
    @Test void throwWithoutHeldPropDoesNotCreateFlight() { throwAt(1); assertTrue(sample(2).flights().isEmpty()); }
    @Test void animationMarkerIsReplayedAtClipNormalizedTime() {
        var c=new EntityAnimationClip("Animation",1,5); c.addEvent(new AnimationEvent(.5f,new EntityActionEvent(EntityActionClip.ActionType.ATTACH_PROP,Map.of("propId","stone","slot","MAIN_HAND")))); track.addClip(c);
        assertTrue(sample(2).held().isEmpty()); assertFalse(sample(3).held().isEmpty()); assertFalse(sample(6).held().isEmpty());
        c.setMuted(true); assertTrue(sample(6).held().isEmpty());
    }
    @Test void reconcileReusesHeldPropsAndCleansFlightsOnRewind() {
        attach(1,"stone"); var runtime=new ActorPropRuntime(); runtime.sync(sample(1)); var prop=runtime.getProp(ActorPropSlot.MAIN_HAND);
        runtime.sync(sample(1.5f)); assertSame(prop,runtime.getProp(ActorPropSlot.MAIN_HAND)); throwAt(2); runtime.sync(sample(3));
        assertEquals(1,runtime.getTimelineProjectiles().size()); assertFalse(runtime.hasProp(ActorPropSlot.MAIN_HAND));
        runtime.sync(sample(1)); assertTrue(runtime.getTimelineProjectiles().isEmpty()); assertTrue(runtime.hasProp(ActorPropSlot.MAIN_HAND));
        runtime.dispose(); assertTrue(runtime.getAllProps().isEmpty());
    }
    @Test void overriddenAnimationMarkerDoesNotMutatePropState() {
        var c=new EntityAnimationClip("Old",1,5);
        c.addEvent(new AnimationEvent(.5f,new EntityActionEvent(EntityActionClip.ActionType.ATTACH_PROP,Map.of("propId","stone","slot","MAIN_HAND"))));
        track.addClip(c); track.addClip(new EntityAnimationClip("New",2,4));
        assertTrue(sample(6).held().isEmpty());
    }
    @Test void offsetsSurviveReplayAndInvalidTimeReturnsEmptyState() {
        var c=attach(1,"stone"); c.setParam("offsetX",2); c.setParam("rotationZ",45);
        assertEquals(45,sample(1).held().get(ActorPropSlot.MAIN_HAND).rz());
        throwAt(2); assertEquals(new Vec3d(4,2,0),sample(2).flights().getFirst().position());
        assertTrue(sample(Float.NaN).held().isEmpty()); assertTrue(sample(-1).flights().isEmpty());
    }
    @Test void diagnosticsKeepEventTimesAndIgnoreFutureOrMutedEvents() {
        attach(1,"missing"); throwAt(2);
        var muted=attach(3,"missing"); muted.setMuted(true); attach(8,"missing");
        var reasons=new ArrayList<com.director.actor.runtime.ActorDiagnosticReason>(); var times=new ArrayList<Float>();
        ActorPropTimelineSampler.sampleTransforms(track,4,(t,slot,a)->null,(t,r)->{ times.add(t); reasons.add(r); });
        assertEquals(List.of(1f,2f),times);
        assertEquals(List.of(com.director.actor.runtime.ActorDiagnosticReason.PROP_PARAMETERS,
                com.director.actor.runtime.ActorDiagnosticReason.PROP_EMPTY_SLOT),reasons);
    }
    @Test void unavailableLaunchReportsOnlyWhileFlightIsActive() {
        attach(1,"stone"); throwAt(2); var reasons=new ArrayList<com.director.actor.runtime.ActorDiagnosticReason>();
        ActorPropTimelineSampler.sampleTransforms(track,3,(t,slot,a)->null,(t,r)->reasons.add(r));
        assertEquals(List.of(com.director.actor.runtime.ActorDiagnosticReason.PROP_LAUNCH),reasons);
        reasons.clear(); ActorPropTimelineSampler.sampleTransforms(track,5,(t,slot,a)->null,(t,r)->reasons.add(r));
        assertTrue(reasons.isEmpty());
    }
    @Test void missingSocketIsReportedButIntentionalHideIsSilentAndRecoveryClearsIt() {
        var prop=new RuntimeProp(PropRegistry.getInstance().get("stone").orElseThrow(),ActorPropSlot.MAIN_HAND);
        prop.updateSocketTransform(null,new org.joml.Matrix4f());
        assertEquals(com.director.actor.runtime.ActorDiagnosticReason.PROP_SOCKET,prop.previewFailure(true));
        assertNull(prop.previewFailure(false));
        prop.updateSocketTransform(new com.director.actor.rig.skeleton.RigPose(com.director.actor.rig.skeleton.HumanoidRig.DEFINITION),new org.joml.Matrix4f());
        assertNull(prop.previewFailure(true));
    }
}

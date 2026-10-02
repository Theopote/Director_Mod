package com.director.ui.actor.edit;

import com.director.actor.*;
import com.director.actor.Actor;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorPosePropertyEditSessionTest {
    TimelineModel model; TimelineTrack track; TimelineCommandHistory history;
    @BeforeEach void setup() {
        ActorRegistryManager.setRegistry(null); ActorRegistryManager.setRegistry(new ActorRegistry());
        var actor = new Actor("Player",ActorType.PLAYER); ActorRegistryManager.getInstance().registerActor(actor);
        model = new TimelineModel(new TimelineViewportState()); model.setCurrentTime(2);
        track = new TimelineTrack("Actor",TrackType.ACTOR); var payload = new ActorTrack();
        payload.setActorRef(new ActorRef(actor.getActorId())); track.setActorTrackPayload(payload); model.addTrack(track);
        history = new TimelineCommandHistory(); TimelineCommandBridge.bind(history);
    }
    @AfterEach void cleanup() { TimelineCommandBridge.bind(null); ActorRegistryManager.setRegistry(null); }
    void attach(EntityClip c) { track.getActorTrackPayload().addClip(c); track.addClip(new EntityTimelineClip(c,0)); }
    ActorPoseClip pose() { var c=new ActorPoseClip("Pose",1,3); attach(c); return c; }
    @Test void anglesAndMasksAreDetachedAndOneUndo() {
        var c=pose(); var edit=new ActorClipPropertyEditSession(model,c); var d=(ActorPoseClip)edit.draft();
        for(int i=0;i<50;i++) d.setHeadYaw(i*.01f);
        d.setOverrideArms(true); d.setLeftArmRoll(.2f); d.setRightArmYaw(.3f); d.setOverrideLegs(true); d.setLeftLegPitch(.4f);
        assertEquals(0,c.getHeadYaw()); assertTrue(edit.commit()); assertEquals(1,history.getUndoSize());
        history.undo(); assertEquals(0,c.getHeadYaw()); assertFalse(c.isOverrideArms()); history.redo();
        assertEquals(.49f,c.getHeadYaw(),.00001f); assertEquals(.2f,c.getLeftArmRoll()); assertEquals(.4f,c.getLeftLegPitch());
    }
    @Test void invalidHiddenAngleRejectsAtomically() {
        var c=pose(); var edit=new ActorClipPropertyEditSession(model,c); var d=(ActorPoseClip)edit.draft();
        d.setHeadYaw(.5f); d.setBodyRoll(Float.NaN); assertThrows(IllegalArgumentException.class,edit::commit);
        assertEquals(0,c.getHeadYaw()); assertEquals(0,history.getUndoSize());
    }
    @Test void unsupportedActorCannotAuthorFixedHumanoidPose() {
        var actor=ActorRegistryManager.getInstance().getActor(track.getActorTrackPayload().getActorRef().getActorId()).orElseThrow();
        actor.setType(ActorType.COW); var c=pose(); var edit=new ActorClipPropertyEditSession(model,c);
        ((ActorPoseClip)edit.draft()).setHeadYaw(.4f); assertThrows(IllegalArgumentException.class,edit::commit);
        assertFalse(ActorBehaviorCapabilities.supportsLegacyPose(actor)); assertEquals(0,c.getHeadYaw());
        assertTrue(new ActorClipPropertyEditSession(model,c).commit()); assertEquals(0,history.getUndoSize());
    }
    @Test void genericKeysAndWrapperArePreserved() {
        var c=pose(); var wrapper=c.getTimelineClip(); var key=new EntityClipKeyframe(2); c.addKeyframe(key);
        var edit=new ActorClipPropertyEditSession(model,c); ((ActorPoseClip)edit.draft()).setOverrideHead(false); assertTrue(edit.commit());
        history.undo(); history.redo(); assertSame(key,c.getKeyframes().getFirst()); assertSame(wrapper,c.getTimelineClip()); assertEquals(3,c.getEndTime());
    }
    @Test void contextChangeDiscardsAndSourceChangeBlocksUndo() {
        var c=pose(); var edit=new ActorClipPropertyEditSession(model,c); ((ActorPoseClip)edit.draft()).setHeadPitch(.2f);
        model.setCurrentTime(3); assertFalse(edit.commit()); assertEquals(0,c.getHeadPitch()); model.setCurrentTime(2);
        edit=new ActorClipPropertyEditSession(model,c); ((ActorPoseClip)edit.draft()).setHeadPitch(.2f); edit.commit();
        c.setOverrideBody(true); assertThrows(IllegalArgumentException.class,history::undo); assertEquals(1,history.getUndoSize());
        c.setOverrideBody(false); history.undo(); assertEquals(0,c.getHeadPitch());
    }
    @Test void actorTypeChangeInvalidatesPendingEdit() {
        var c=pose(); var edit=new ActorClipPropertyEditSession(model,c); ((ActorPoseClip)edit.draft()).setHeadYaw(.3f);
        var actor=ActorRegistryManager.getInstance().getActor(track.getActorTrackPayload().getActorRef().getActorId()).orElseThrow();
        actor.setType(ActorType.VILLAGER); assertFalse(edit.commit()); assertEquals(0,c.getHeadYaw());
    }
    @Test void customRigRequiresHumanoidTopologyRatherThanOnlyMatchingBoneNames() {
        com.director.actor.rig.skeleton.BuiltInCustomRigs.registerDefaults();
        var actor=ActorRegistryManager.getInstance().getActor(track.getActorTrackPayload().getActorRef().getActorId()).orElseThrow();
        actor.setType(ActorType.CUSTOM);
        actor.setCustomRigId(com.director.actor.rig.skeleton.BuiltInCustomRigs.CHIBI_HUMANOID.id());
        assertTrue(ActorBehaviorCapabilities.supportsLegacyPose(actor));
        var c=pose(); var edit=new ActorClipPropertyEditSession(model,c);
        ((ActorPoseClip)edit.draft()).setHeadYaw(.3f); assertTrue(edit.commit()); history.undo();
        actor.setCustomRigId(com.director.actor.rig.skeleton.BuiltInCustomRigs.SIMPLE_ROBOT.id());
        assertFalse(ActorBehaviorCapabilities.supportsLegacyPose(actor));
        edit=new ActorClipPropertyEditSession(model,c); ((ActorPoseClip)edit.draft()).setHeadYaw(.3f);
        assertThrows(IllegalArgumentException.class,edit::commit);
    }
}

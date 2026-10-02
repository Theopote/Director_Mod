package com.director.ui.actor.edit;

import com.director.actor.*;
import com.director.actor.Actor;
import com.director.actor.action.ActorActionType;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import org.junit.jupiter.api.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ActorBehaviorPropertyEditSessionTest {
    TimelineModel model; TimelineTrack track; Actor actor; TimelineCommandHistory history;
    @BeforeEach void setup() {
        ActorRegistryManager.setRegistry(null); ActorRegistryManager.setRegistry(new ActorRegistry());
        actor = new Actor("Player", ActorType.PLAYER); ActorRegistryManager.getInstance().registerActor(actor);
        model = new TimelineModel(new TimelineViewportState()); model.setCurrentTime(2);
        track = track(actor); history = new TimelineCommandHistory(); TimelineCommandBridge.bind(history);
    }
    @AfterEach void cleanup() { TimelineCommandBridge.bind(null); ActorRegistryManager.setRegistry(null); }
    TimelineTrack track(Actor definition) {
        var owner = new TimelineTrack(definition.getName(), TrackType.ACTOR); var payload = new ActorTrack();
        payload.setActorRef(new ActorRef(definition.getActorId())); owner.setActorTrackPayload(payload); model.addTrack(owner); return owner;
    }
    Actor other() { var a = new Actor("Other", ActorType.PLAYER); ActorRegistryManager.getInstance().registerActor(a); return a; }
    void attach(TimelineTrack owner, EntityClip clip) { owner.getActorTrackPayload().addClip(clip); owner.addClip(new EntityTimelineClip(clip, 0)); }
    ActorActionClip action() { var c = new ActorActionClip("Action", 1,3); attach(track, c); return c; }
    ActorRelationClip relation(TimelineTrack owner, UUID target, float start, float end) {
        var c = new ActorRelationClip("Relation", start,end); c.setTargetActorId(target); attach(owner, c); return c;
    }
    @Test void actionSliderTimeAndLoopAreOneDetachedUndo() {
        var c = action(); var wrapper = c.getTimelineClip(); var edit = new ActorClipPropertyEditSession(model,c);
        var draft = (ActorActionClip) edit.draft();
        for (int i=0;i<50;i++) draft.setIntensity(i/100f);
        draft.setActionType(ActorActionType.RUN); draft.setLoop(false); draft.setTimeRange(.5f,4);
        assertEquals(1,c.getIntensity()); assertEquals(3,wrapper.getEndTime()); assertEquals(0,history.getUndoSize());
        assertTrue(edit.commit()); assertEquals(.49f,c.getIntensity()); assertEquals(4,wrapper.getEndTime());
        assertEquals(.5f,wrapper.getStartTime()); assertEquals(1,history.getUndoSize());
        history.undo(); assertEquals(1,c.getIntensity()); assertEquals(3,wrapper.getEndTime()); assertTrue(c.isLoop());
        history.redo(); assertEquals(ActorActionType.RUN,c.getActionType()); assertEquals(4,c.getEndTime()); assertFalse(c.isLoop());
    }
    @Test void unsupportedActionUsesOwnersCapabilitiesAndWritesNothing() {
        actor.setType(ActorType.COW); var c = action(); var edit = new ActorClipPropertyEditSession(model,c);
        ((ActorActionClip)edit.draft()).setActionType(ActorActionType.DANCE);
        assertThrows(IllegalArgumentException.class,edit::commit); assertEquals(ActorActionType.IDLE,c.getActionType());
        assertTrue(ActorBehaviorCapabilities.supportsAction(actor,ActorActionType.GRAZE));
        assertFalse(ActorBehaviorCapabilities.supportsAction(actor,ActorActionType.CARRY)); assertEquals(0,history.getUndoSize());
    }
    @Test void capabilityChangeInvalidatesGesture() {
        var c = action(); var edit = new ActorClipPropertyEditSession(model,c);
        ((ActorActionClip)edit.draft()).setActionType(ActorActionType.DANCE); actor.setType(ActorType.COW);
        assertFalse(edit.commit()); assertEquals(ActorActionType.IDLE,c.getActionType()); assertEquals(0,history.getUndoSize());
    }
    @Test void invalidAndOverlappingActionTimesAreAtomic() {
        var c = action(); var edit = new ActorClipPropertyEditSession(model,c);
        ((ActorActionClip)edit.draft()).setTimeRange(-1,3); assertThrows(IllegalArgumentException.class,edit::commit);
        attach(track,new ActorActionClip("Later",4,6)); edit = new ActorClipPropertyEditSession(model,c);
        ((ActorActionClip)edit.draft()).setTimeRange(1,5); ((ActorActionClip)edit.draft()).setIntensity(.5f);
        assertThrows(IllegalArgumentException.class,edit::commit); assertEquals(3,c.getEndTime()); assertEquals(1,c.getIntensity());
        assertEquals(0,history.getUndoSize());
    }
    @Test void redoChecksNewActionConflictsWithoutLosingHistory() {
        var c = action(); var edit = new ActorClipPropertyEditSession(model,c); ((ActorActionClip)edit.draft()).setTimeRange(1,5);
        assertTrue(edit.commit()); history.undo(); attach(track,new ActorActionClip("Later",4,6));
        assertThrows(IllegalArgumentException.class,history::redo); assertEquals(3,c.getEndTime()); assertEquals(1,history.getRedoSize());
    }
    @Test void relationshipParametersAndTargetAreDetachedAndUndoTogether() {
        var target = other(); var c = relation(track,null,1,3); var edit = new ActorClipPropertyEditSession(model,c);
        var draft = (ActorRelationClip)edit.draft(); draft.setTargetActorId(target.getActorId()); draft.setRelationType(ActorRelationClip.RelationType.FOLLOW);
        for (int i=1;i<30;i++) draft.setDistance(i/2f);
        draft.setHeightOffset(.5f); draft.setMaintainLookAt(false);
        assertNull(c.getTargetActorId()); assertEquals(2,c.getDistance()); assertTrue(edit.commit());
        assertEquals(14.5f,c.getDistance()); assertEquals(1,history.getUndoSize()); history.undo();
        assertNull(c.getTargetActorId()); assertEquals(2,c.getDistance()); history.redo(); assertEquals(target.getActorId(),c.getTargetActorId());
    }
    @Test void selfMissingAndUnimplementedRelationshipTargetsReject() {
        var c = relation(track,null,1,3); var edit = new ActorClipPropertyEditSession(model,c);
        ((ActorRelationClip)edit.draft()).setTargetActorId(actor.getActorId()); assertThrows(IllegalArgumentException.class,edit::commit);
        edit = new ActorClipPropertyEditSession(model,c); ((ActorRelationClip)edit.draft()).setTargetActorId(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,edit::commit);
        for (var type : new ActorRelationClip.RelationType[]{ActorRelationClip.RelationType.PASS_OBJECT,ActorRelationClip.RelationType.MIRROR}) {
            edit = new ActorClipPropertyEditSession(model,c); ((ActorRelationClip)edit.draft()).setRelationType(type);
            assertThrows(IllegalArgumentException.class,edit::commit);
        }
        assertNull(c.getTargetActorId()); assertEquals(0,history.getUndoSize());
    }
    @Test void simultaneousRelationCycleIsRejected() {
        var b = other(); var bTrack = track(b); relation(bTrack,actor.getActorId(),1,3);
        var c = relation(track,null,1,3); var edit = new ActorClipPropertyEditSession(model,c);
        ((ActorRelationClip)edit.draft()).setTargetActorId(b.getActorId()); assertThrows(IllegalArgumentException.class,edit::commit);
        assertNull(c.getTargetActorId()); assertEquals(0,history.getUndoSize());
    }
    @Test void nonOverlappingRelationChainIsAllowedAndMutedLinksAreIgnored() {
        var b = other(); var bTrack = track(b); var back = relation(bTrack,actor.getActorId(),4,6);
        var c = relation(track,null,1,3); var edit = new ActorClipPropertyEditSession(model,c);
        ((ActorRelationClip)edit.draft()).setTargetActorId(b.getActorId()); assertTrue(edit.commit()); history.undo();
        back.setTimeRange(1,3); back.setMuted(true); edit = new ActorClipPropertyEditSession(model,c);
        ((ActorRelationClip)edit.draft()).setTargetActorId(b.getActorId()); assertTrue(edit.commit());
    }
    @Test void relationCycleRequiresOneCommonTimeAcrossEntireChain() {
        var b = other(); var d = other(); relation(track(b),d.getActorId(),1,2); relation(track(d),actor.getActorId(),3,4);
        var c = relation(track,null,1,4); var edit = new ActorClipPropertyEditSession(model,c);
        ((ActorRelationClip)edit.draft()).setTargetActorId(b.getActorId()); assertTrue(edit.commit());
    }
    @Test void redoRechecksRelationshipGraphAndRetainsHistoryOnConflict() {
        var b = other(); var bTrack = track(b); var c = relation(track,null,1,3);
        var edit = new ActorClipPropertyEditSession(model,c); ((ActorRelationClip)edit.draft()).setTargetActorId(b.getActorId());
        assertTrue(edit.commit()); history.undo(); relation(bTrack,actor.getActorId(),1,3);
        assertThrows(IllegalArgumentException.class,history::redo); assertNull(c.getTargetActorId()); assertEquals(1,history.getRedoSize());
    }
    @Test void noOpCancelAndNonFiniteRelationValuesDoNotWrite() {
        var c = relation(track,null,1,3); assertTrue(new ActorClipPropertyEditSession(model,c).commit());
        var edit = new ActorClipPropertyEditSession(model,c); ((ActorRelationClip)edit.draft()).setDistance(8); edit.cancel(); assertFalse(edit.commit());
        edit = new ActorClipPropertyEditSession(model,c); ((ActorRelationClip)edit.draft()).setAngle(Float.NaN);
        assertThrows(IllegalArgumentException.class,edit::commit); assertEquals(0,c.getAngle()); assertEquals(2,c.getDistance()); assertEquals(0,history.getUndoSize());
    }
    @Test void decimalMinimumDurationSurvivesFloatRounding() {
        var c = new ActorActionClip("Short",2,2.1f); attach(track,c);
        var edit = new ActorClipPropertyEditSession(model,c); ((ActorActionClip)edit.draft()).setIntensity(.5f);
        assertTrue(edit.commit()); assertEquals(.5f,c.getIntensity()); history.undo(); assertEquals(1,c.getIntensity());
    }
    @Test void undoRestoresIgnoredLegacyRelationshipWithoutTreatingItAsRuntimeDependency() {
        var b = other(); relation(track(b),actor.getActorId(),1,3);
        var c = relation(track,b.getActorId(),1,3); c.setRelationType(ActorRelationClip.RelationType.MIRROR);
        var edit = new ActorClipPropertyEditSession(model,c); var draft = (ActorRelationClip)edit.draft();
        draft.setRelationType(ActorRelationClip.RelationType.FOLLOW); draft.setTargetActorId(null); assertTrue(edit.commit());
        history.undo(); assertEquals(ActorRelationClip.RelationType.MIRROR,c.getRelationType()); assertEquals(b.getActorId(),c.getTargetActorId());
        history.redo(); assertNull(c.getTargetActorId()); assertEquals(ActorRelationClip.RelationType.FOLLOW,c.getRelationType());
    }
}

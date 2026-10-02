package com.director.ui.actor.edit;

import com.director.actor.*;
import com.director.actor.Actor;
import com.director.actor.prop.*;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorPropSpawnPropertyEditSessionTest {
    TimelineModel model; TimelineTrack track; Actor actor; TimelineCommandHistory history;
    @BeforeEach void setup() {
        ActorRegistryManager.setRegistry(null); ActorRegistryManager.setRegistry(new ActorRegistry());
        actor = new Actor("Player",ActorType.PLAYER); ActorRegistryManager.getInstance().registerActor(actor);
        model = new TimelineModel(new TimelineViewportState()); model.setCurrentTime(2);
        track = new TimelineTrack("Actor",TrackType.ACTOR); var payload = new ActorTrack();
        payload.setActorRef(new ActorRef(actor.getActorId())); track.setActorTrackPayload(payload); model.addTrack(track);
        history = new TimelineCommandHistory(); TimelineCommandBridge.bind(history);
    }
    @AfterEach void cleanup() { TimelineCommandBridge.bind(null); ActorRegistryManager.setRegistry(null); }
    void attach(EntityClip clip) { track.getActorTrackPayload().addClip(clip); track.addClip(new EntityTimelineClip(clip,0)); }
    EntitySpawnClip spawn() { var clip = new EntitySpawnClip("Spawn",1,3); clip.setPosition(1,2,3); attach(clip); return clip; }
    EntityActionClip event() {
        var clip = new EntityActionClip("Prop",1,3); clip.setActionType(EntityActionClip.ActionType.ATTACH_PROP);
        clip.setParam("propId","stone"); clip.setParam("slot","MAIN_HAND"); attach(clip); return clip;
    }
    @Test void spawnPositionVisibilityAndDurationAreOneUndoWithStableWrapper() {
        var source = spawn(); var wrapper = source.getTimelineClip(); var edit = new ActorClipPropertyEditSession(model,source);
        var draft = (EntitySpawnClip)edit.draft();
        for (int i=0;i<50;i++) draft.setPosition(i,2,3);
        draft.setTimeRange(1,5); draft.setVisibleOnSpawn(false); draft.setYawDeg(60); draft.setPitchDeg(30);
        assertEquals(1,source.getPosition().x); assertEquals(3,wrapper.getEndTime()); assertTrue(edit.commit());
        assertEquals(1,history.getUndoSize()); assertEquals(5,wrapper.getEndTime()); history.undo();
        assertEquals(new Vector3f(1,2,3),source.getPosition()); assertTrue(source.isVisibleOnSpawn()); history.redo();
        assertEquals(49,source.getPosition().x); assertFalse(source.isVisibleOnSpawn()); assertSame(wrapper,source.getTimelineClip());
    }
    @Test void invalidSpawnPoseAndTimeDoNotWriteSource() {
        var source = spawn(); var edit = new ActorClipPropertyEditSession(model,source);
        ((EntitySpawnClip)edit.draft()).setPosition(Float.NaN,0,0); assertThrows(IllegalArgumentException.class,edit::commit);
        edit = new ActorClipPropertyEditSession(model,source); ((EntitySpawnClip)edit.draft()).setPitchDeg(100);
        assertThrows(IllegalArgumentException.class,edit::commit);
        edit = new ActorClipPropertyEditSession(model,source); edit.draft().setTimeRange(1,Float.POSITIVE_INFINITY);
        assertThrows(IllegalArgumentException.class,edit::commit); assertEquals(3,source.getEndTime()); assertEquals(0,history.getUndoSize());
    }
    @Test void spawnRangeConflictIsAtomicAndRedoRechecksIt() {
        var source = spawn(); var edit = new ActorClipPropertyEditSession(model,source); edit.draft().setTimeRange(1,5);
        assertTrue(edit.commit()); history.undo(); attach(new EntitySpawnClip("Later",4,6));
        assertThrows(IllegalArgumentException.class,history::redo); assertEquals(3,source.getEndTime()); assertEquals(1,history.getRedoSize());
        edit = new ActorClipPropertyEditSession(model,source); edit.draft().setTimeRange(1,5);
        assertThrows(IllegalArgumentException.class,edit::commit);
    }
    @Test void shortSpawnAndNoOpPreserveKeyIdentity() {
        var source = spawn(); source.setTimeRange(2,2.1f); var key = new EntityClipKeyframe(2.05f); source.addKeyframe(key);
        assertTrue(new ActorClipPropertyEditSession(model,source).commit()); assertEquals(0,history.getUndoSize());
        var edit = new ActorClipPropertyEditSession(model,source); ((EntitySpawnClip)edit.draft()).setVisibleOnSpawn(false);
        assertTrue(edit.commit()); history.undo(); assertSame(key,source.getKeyframes().getFirst());
    }
    @Test @SuppressWarnings("unchecked") void eventDeepCopiesNestedMetadataAndUndoUsesIndependentSnapshots() {
        var source = event(); source.setParam("extra",new LinkedHashMap<>(Map.of("list",new ArrayList<>(List.of("old")))));
        var edit = new ActorClipPropertyEditSession(model,source); var draft = (EntityActionClip)edit.draft();
        ((List<Object>)((Map<String,Object>)draft.getParam("extra")).get("list")).add("new"); draft.setParam("propId","dirt");
        assertEquals(List.of("old"),((Map<?,?>)source.getParam("extra")).get("list")); assertTrue(edit.commit());
        ((List<Object>)((Map<String,Object>)draft.getParam("extra")).get("list")).add("late draft change");
        assertEquals(List.of("old","new"),((Map<?,?>)source.getParam("extra")).get("list")); history.undo();
        assertEquals("stone",source.getParam("propId")); assertEquals(List.of("old"),((Map<?,?>)source.getParam("extra")).get("list"));
        history.redo(); assertEquals("dirt",source.getParam("propId")); assertEquals(1,history.getUndoSize());
    }
    @Test void malformedPropIdSlotAndNumericTypeRejectBeforeWriting() {
        var source = event(); var edit = new ActorClipPropertyEditSession(model,source);
        ((EntityActionClip)edit.draft()).setParam("propId","missing"); assertThrows(IllegalArgumentException.class,edit::commit);
        edit = new ActorClipPropertyEditSession(model,source); ((EntityActionClip)edit.draft()).setParam("slot","INVALID");
        assertThrows(IllegalArgumentException.class,edit::commit);
        edit = new ActorClipPropertyEditSession(model,source); ((EntityActionClip)edit.draft()).setParam("offsetX","1.0");
        assertThrows(IllegalArgumentException.class,edit::commit); assertEquals("stone",source.getParam("propId")); assertEquals(0,history.getUndoSize());
    }
    @Test void nonHumanoidAndNonHandThrowSlotsAreRejected() {
        actor.setType(ActorType.COW); var source = event(); var edit = new ActorClipPropertyEditSession(model,source);
        ((EntityActionClip)edit.draft()).setParam("propId","dirt"); assertThrows(IllegalArgumentException.class,edit::commit);
        assertFalse(PropEventParameters.supportsSlot(actor,ActorPropSlot.MAIN_HAND,EntityActionClip.ActionType.ATTACH_PROP));
        actor.setType(ActorType.PLAYER); edit = new ActorClipPropertyEditSession(model,source); var draft = (EntityActionClip)edit.draft();
        draft.setActionType(EntityActionClip.ActionType.THROW_PROP); draft.setParam("slot","HEAD");
        for (String key : List.of("targetX","targetY","targetZ")) draft.setParam(key,1);
        assertThrows(IllegalArgumentException.class,edit::commit); assertEquals(0,history.getUndoSize());
    }
    @Test void switchEventAndOptionalOldSlotCommitTogether() {
        var source = event(); var edit = new ActorClipPropertyEditSession(model,source); var draft = (EntityActionClip)edit.draft();
        draft.setActionType(EntityActionClip.ActionType.SWITCH_PROP); draft.setParam("newSlot","OFF_HAND"); draft.setParam("newPropId","dirt");
        assertTrue(edit.commit()); assertEquals(EntityActionClip.ActionType.SWITCH_PROP,source.getActionType());
        history.undo(); assertNull(source.getParam("newSlot")); assertEquals(EntityActionClip.ActionType.ATTACH_PROP,source.getActionType());
        history.redo(); assertEquals("OFF_HAND",source.getParam("newSlot")); assertEquals("stone",source.getParam("propId"));
    }
    @Test void throwRequiresFiniteDestinationAndPositiveFlightTime() {
        var source = event(); var edit = new ActorClipPropertyEditSession(model,source); var draft = (EntityActionClip)edit.draft();
        draft.setActionType(EntityActionClip.ActionType.THROW_PROP); assertThrows(IllegalArgumentException.class,edit::commit);
        for (String key : List.of("targetX","targetY","targetZ")) draft.setParam(key,1);
        draft.setParam("flightTime",0); assertThrows(IllegalArgumentException.class,edit::commit);
        draft.setParam("flightTime",1); draft.setParam("arcHeight",-1); assertThrows(IllegalArgumentException.class,edit::commit);
        draft.setParam("arcHeight",1); assertTrue(edit.commit()); history.undo(); assertEquals(EntityActionClip.ActionType.ATTACH_PROP,source.getActionType());
    }
    @Test void eventUndoChecksNestedSourceChangesAndKeepsHistory() {
        var source = event(); var edit = new ActorClipPropertyEditSession(model,source); ((EntityActionClip)edit.draft()).setParam("propId","dirt");
        assertTrue(edit.commit()); source.setParam("propId","oak_planks"); assertThrows(IllegalArgumentException.class,history::undo);
        assertEquals(1,history.getUndoSize()); source.setParam("propId","dirt"); history.undo(); assertEquals("stone",source.getParam("propId"));
    }
    @Test void spawnAndEventPendingEditsCancelOnContextChanges() {
        var source = event(); var edit = new ActorClipPropertyEditSession(model,source); ((EntityActionClip)edit.draft()).setParam("propId","dirt");
        model.setCurrentTime(3); assertFalse(edit.commit()); assertEquals("stone",source.getParam("propId")); model.setCurrentTime(2);
        var spawn = spawn(); edit = new ActorClipPropertyEditSession(model,spawn); ((EntitySpawnClip)edit.draft()).setPosition(8,0,0);
        edit.cancel(); assertFalse(edit.commit()); assertEquals(1,spawn.getPosition().x); assertEquals(0,history.getUndoSize());
    }
    @Test void unsupportedEventsAndMutableNonJsonValuesAreRejected() {
        assertFalse(PropEventParameters.supports(EntityActionClip.ActionType.PLAY_SOUND));
        assertTrue(PropEventParameters.supports(EntityActionClip.ActionType.THROW_PROP));
        assertThrows(IllegalArgumentException.class,()->PropEventParameters.copy(Map.of("vector",new Vector3f()),true));
        var cyclic = new HashMap<String,Object>(); cyclic.put("self",cyclic);
        assertThrows(IllegalArgumentException.class,()->PropEventParameters.copy(cyclic,true));
        var source = event(); var edit = new ActorClipPropertyEditSession(model,source); ((EntityActionClip)edit.draft()).setActionType(EntityActionClip.ActionType.PLAY_SOUND);
        assertThrows(IllegalArgumentException.class,edit::commit); assertEquals(0,history.getUndoSize());
    }
    @Test void nestedNonFiniteMetadataCannotBeCommitted() {
        var source = event(); var edit = new ActorClipPropertyEditSession(model,source);
        ((EntityActionClip)edit.draft()).setParam("extra",List.of(Map.of("invalid",Double.POSITIVE_INFINITY)));
        assertThrows(IllegalArgumentException.class,edit::commit); assertNull(source.getParam("extra")); assertEquals(0,history.getUndoSize());
    }
    @Test void projectileTakesPropOwnershipWithoutDetachingDisposal() {
        var runtime = new ActorPropRuntime(); var prop = new RuntimeProp(PropRegistry.getInstance().get("stone").orElseThrow(),ActorPropSlot.MAIN_HAND);
        runtime.attach(ActorPropSlot.MAIN_HAND,prop); assertSame(prop,runtime.release(ActorPropSlot.MAIN_HAND));
        assertFalse(runtime.hasProp(ActorPropSlot.MAIN_HAND)); assertNull(runtime.release(ActorPropSlot.MAIN_HAND));
        var start = new net.minecraft.util.math.Vec3d(1,2,3); var end = new net.minecraft.util.math.Vec3d(4,5,6);
        prop.setWorldTransform(start); var projectile = new RuntimeProjectile(prop,start,end,1,1); projectile.update(0);
        assertFalse(prop.isBoundToActor()); assertEquals(start,prop.getWorldPosition()); assertFalse(projectile.isFinished());
    }
}

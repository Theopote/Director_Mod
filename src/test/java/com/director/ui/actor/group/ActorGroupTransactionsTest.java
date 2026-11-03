package com.director.ui.actor.group;

import com.director.actor.Actor;
import com.director.actor.ActorGroup;
import com.director.actor.ActorRef;
import com.director.actor.ActorRegistry;
import com.director.actor.ActorRegistryManager;
import com.director.actor.ActorType;
import com.director.actor.action.ActorActionType;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ActorGroupTransactionsTest {
    private final TimelineModel model = new TimelineModel(new TimelineViewportState());
    private final TimelineCommandHistory history = new TimelineCommandHistory();
    private final ActorGroup group = new ActorGroup("Cast");
    private final ActorGroupTimelineCommandService service = ActorGroupTimelineCommandService.getInstance();
    @BeforeEach void setup() {
        ActorRegistryManager.setRegistry(null);
        ActorRegistryManager.setRegistry(new ActorRegistry());
        TimelineCommandBridge.bind(history);
        model.setCurrentTime(2);
    }
    @AfterEach void cleanup() { TimelineCommandBridge.bind(null); ActorRegistryManager.setRegistry(null); }
    private TimelineTrack member(ActorType type) {
        var actor = new Actor("Member", type);
        ActorRegistryManager.getInstance().registerActor(actor);
        group.addActor(actor.getActorId());
        var payload = new ActorTrack(); payload.setActorRef(new ActorRef(actor.getActorId()));
        var track = new TimelineTrack(actor.getName(), TrackType.ACTOR); track.setActorTrackPayload(payload);
        model.addTrack(track); return track;
    }
    private EntityTimelineClip attach(TimelineTrack track, EntityClip clip) {
        track.getActorTrackPayload().addClip(clip);
        var wrapper = new EntityTimelineClip(clip, track.getActorTrackPayload().getColorRgba());
        track.addClip(wrapper); return wrapper;
    }
    @Test void preflightReportsHeterogeneousLockedAndMissingMembersWithoutMutation() {
        var player = member(ActorType.PLAYER);
        var cow = member(ActorType.COW);
        var locked = member(ActorType.PLAYER); locked.setLocked(true);
        var noTrack = new Actor("Unused", ActorType.PLAYER);
        ActorRegistryManager.getInstance().registerActor(noTrack); group.addActor(noTrack.getActorId());
        group.addActor(UUID.randomUUID());
        var preview = service.previewActions(model, group, ActorActionType.DANCE, 2);
        assertEquals(1, preview.eligible().size()); assertEquals(4, preview.skipped().size());
        assertEquals(4, preview.skipped().stream().map(ActorGroupTimelineCommandService.SkippedMember::reason).distinct().count());
        assertTrue(player.getClips().isEmpty()); assertTrue(cow.getClips().isEmpty());
        assertEquals(1, service.createActionClips(model, group, ActorActionType.DANCE, 2, 1, true));
        assertTrue(cow.getClips().isEmpty()); assertTrue(locked.getClips().isEmpty());
        assertTrue(history.undo()); assertTrue(player.getClips().isEmpty()); assertFalse(history.undo());
    }
    @Test void actionUndoRestoresOriginalIntervalsWrappersAndUnrelatedClipsForEntireGroup() {
        var a = member(ActorType.PLAYER); var b = member(ActorType.COW);
        var old = new ActorActionClip("Old", 0, 10); old.setActionType(ActorActionType.IDLE);
        var oldWrapper = attach(a, old);
        var spawn = new EntitySpawnClip("Spawn", 0, 10); var spawnWrapper = attach(a, spawn);
        assertEquals(1, service.previewActions(model, group, ActorActionType.WALK, 2).overwrittenMembers());
        assertEquals(2, service.createActionClips(model, group, ActorActionType.WALK, 2, 1, true));
        var generated = List.copyOf(a.getActorTrackPayload().getActionClips());
        assertEquals(3, generated.size()); assertTrue(a.getClips().contains(spawnWrapper));
        assertTrue(history.undo());
        assertEquals(List.of(old), a.getActorTrackPayload().getActionClips());
        assertTrue(a.getClips().contains(oldWrapper)); assertSame(oldWrapper, old.getTimelineClip());
        assertSame(a, oldWrapper.getParentTrack()); assertTrue(b.getClips().isEmpty()); assertFalse(history.undo());
        assertTrue(history.redo()); assertEquals(generated, a.getActorTrackPayload().getActionClips());
        assertEquals(1, b.getActorTrackPayload().getActionClips().size());
        assertTrue(a.getClips().contains(spawnWrapper));
    }
    @Test void routeSkipsSourceAndConflictingOrLockedTracksAndUndoKeepsSource() {
        var sourceTrack = member(ActorType.PLAYER); var target = member(ActorType.COW);
        var conflicting = member(ActorType.PLAYER); var locked = member(ActorType.PLAYER); locked.setLocked(true);
        var source = new ActorRouteClip("Road", 0, 8); source.setWaypoints(List.of(new Vector3f(),new Vector3f(5,0,0)));
        source.setSmoothTravel(true);source.setEndpointTurnDuration(.7f);source.setLoopMode(ActorRouteClip.LoopMode.PING_PONG);
        var sourceWrapper = attach(sourceTrack, source);
        var transform = new EntityTransformClip("Move", 1, 4); attach(conflicting, transform);
        UUID sourceId = sourceTrack.getActorTrackPayload().getActorRef().getActorId();
        var preview = service.previewRoutes(model, group, sourceId);
        assertEquals(1, preview.eligible().size()); assertEquals(3, preview.skipped().size());
        assertEquals(1, service.copyLatestRouteFromActor(model, group, sourceId));
        var copy = target.getActorTrackPayload().getClips().getFirst();
        assertTrue(history.undo()); assertTrue(target.getClips().isEmpty()); assertTrue(target.getActorTrackPayload().getClips().isEmpty());
        assertEquals(List.of(source), sourceTrack.getActorTrackPayload().getClips()); assertSame(sourceWrapper, source.getTimelineClip());
        assertTrue(history.redo()); assertSame(copy, target.getActorTrackPayload().getClips().getFirst());
        assertTrue(((ActorRouteClip)copy).isSmoothTravel());assertEquals(.7f,((ActorRouteClip)copy).getEndpointTurnDuration());
        assertFalse(service.previewRoutes(model, group, sourceId).canExecute());
    }
    @Test void placementUndoRestoresExistingSpawnAndRemovesNewSpawnTogether() {
        var a = member(ActorType.PLAYER); var b = member(ActorType.COW);
        var spawn = new EntitySpawnClip("Original", 0, 9); spawn.setPosition(1,2,3); spawn.setYawDeg(20); spawn.setPitchDeg(10);
        var originalWrapper = attach(a, spawn);
        assertEquals(2, service.placeGroup(model, group, new Vec3d(10,20,30), 60, 5));
        assertEquals(10, spawn.getPosition().x); assertEquals(60, spawn.getYawDeg());
        var newSpawn = b.getActorTrackPayload().getClips().getFirst();
        assertTrue(history.undo()); assertEquals(new Vector3f(1,2,3), spawn.getPosition()); assertEquals(20, spawn.getYawDeg());
        assertEquals(10, spawn.getPitchDeg()); assertSame(originalWrapper, spawn.getTimelineClip());
        assertTrue(b.getClips().isEmpty()); assertTrue(b.getActorTrackPayload().getClips().isEmpty());
        assertTrue(history.redo()); assertSame(newSpawn,b.getActorTrackPayload().getClips().getFirst());
        assertEquals(1,b.getClips().size());
    }
    @Test void placementSkipsAnimatedMemberWithoutChangingItsSpawn() {
        var animated = member(ActorType.PLAYER); var plain = member(ActorType.PLAYER);
        var spawn = new EntitySpawnClip("Spawn", 0, 10); spawn.setPosition(1, 2, 3); attach(animated, spawn);
        var transform = new EntityTransformClip("Move", 0, 10);
        transform.setFromPos(10, 2, 3); transform.setToPos(10, 2, 3); attach(animated, transform);
        var preview = service.previewPlacement(model, group);
        assertEquals(1, preview.eligible().size());
        assertEquals(ActorGroupTimelineCommandService.SkipReason.PLACEMENT_CONTROLLED, preview.skipped().getFirst().reason());
        assertEquals(1, service.placeGroup(model, group, new Vec3d(100, 2, 3), 0, 0));
        assertEquals(new Vector3f(1, 2, 3), spawn.getPosition());
        assertEquals(10, new com.director.timeline.playback.entity.EntityClipSampler(animated.getActorTrackPayload()).sampleSnapshot(2).position.x);
        assertTrue(history.undo()); assertTrue(plain.getClips().isEmpty());
        assertTrue(history.redo()); assertEquals(new Vector3f(1, 2, 3), spawn.getPosition());
    }
    @Test void placementUsesFormationYawAndUndoRestoresIt() {
        var a = member(ActorType.PLAYER); var b = member(ActorType.PLAYER);
        group.setFormationType(ActorGroup.FormationType.CIRCLE);
        assertEquals(2, service.placeGroup(model, group, new Vec3d(0, 2, 0), 30, 0));
        assertEquals(30, ((EntitySpawnClip)a.getActorTrackPayload().getClips().getFirst()).getYawDeg());
        assertEquals(210, ((EntitySpawnClip)b.getActorTrackPayload().getClips().getFirst()).getYawDeg());
        assertTrue(history.undo()); assertTrue(a.getClips().isEmpty()); assertTrue(b.getClips().isEmpty());
        assertTrue(history.redo());
        assertEquals(210, ((EntitySpawnClip)b.getActorTrackPayload().getClips().getFirst()).getYawDeg());
    }
    @Test void placementRejectsPlaybackAndMutedTracks() {
        var track = member(ActorType.PLAYER); model.setPlaying(true);
        assertFalse(service.previewPlacement(model, group).canExecute());
        model.setPlaying(false); track.setMuted(true);
        assertFalse(service.previewPlacement(model, group).canExecute());
        assertEquals(0, service.placeGroup(model, group, Vec3d.ZERO, 0, 0));
        assertFalse(history.undo());
    }
    @Test void freePlacementPreservesOffsetsAndSeparatesNewMembers() {
        var a = member(ActorType.PLAYER); var b = member(ActorType.PLAYER); var c = member(ActorType.PLAYER);
        var first = new EntitySpawnClip("First", 0, 10); first.setPosition(1, 2, 3); attach(a, first);
        var second = new EntitySpawnClip("Second", 0, 10); second.setPosition(5, 3, 8); attach(b, second);
        assertEquals(3, service.placeGroup(model, group, new Vec3d(10, 20, 30), 0, 0));
        assertEquals(new Vector3f(10, 20, 30), first.getPosition());
        assertEquals(new Vector3f(14, 21, 35), second.getPosition());
        assertTrue(((EntitySpawnClip)c.getActorTrackPayload().getClips().getFirst()).getPosition().x > 14);
        assertTrue(history.undo()); assertEquals(new Vector3f(5, 3, 8), second.getPosition());
        assertTrue(c.getClips().isEmpty()); assertTrue(history.redo());
        assertEquals(new Vector3f(14, 21, 35), second.getPosition());
    }
    @Test void routeBeforeAllClipsUsesNearestFutureRoute() {
        var source = member(ActorType.PLAYER); var target = member(ActorType.PLAYER);
        var first = new ActorRouteClip("First", 5, 10); first.setWaypoints(List.of(new Vector3f(), new Vector3f(2,0,0))); attach(source, first);
        var last = new ActorRouteClip("Last", 20, 25); last.setWaypoints(List.of(new Vector3f(), new Vector3f(3,0,0))); attach(source, last);
        var id = source.getActorTrackPayload().getActorRef().getActorId();
        assertSame(first, service.findLatestRouteClip(model, id));
        assertEquals(1, service.copyLatestRouteFromActor(model, group, id));
        assertEquals("First", target.getActorTrackPayload().getClips().getFirst().getName());
        assertTrue(history.undo()); assertTrue(target.getClips().isEmpty());
    }
    @Test void actionLoopChoiceSurvivesUndoRedo() {
        var track = member(ActorType.PLAYER);
        for (boolean loop : new boolean[]{false, true}) {
            assertEquals(1, service.createActionClips(model, group, ActorActionType.WALK, 2, 1, loop));
            assertEquals(loop, track.getActorTrackPayload().getActionClips().getFirst().isLoop());
            assertTrue(history.undo()); assertTrue(track.getClips().isEmpty());
            assertTrue(history.redo());
            assertEquals(loop, track.getActorTrackPayload().getActionClips().getFirst().isLoop());
            assertTrue(history.undo());
        }
    }
    @Test void explicitRouteWinsAndRemovedRouteDoesNotFallBack() {
        var source = member(ActorType.PLAYER); var target = member(ActorType.PLAYER);
        var first = new ActorRouteClip("First", 0, 5); first.setWaypoints(List.of(new Vector3f(), new Vector3f(2,0,0))); attach(source, first);
        var chosen = new ActorRouteClip("Chosen", 10, 15); chosen.setWaypoints(List.of(new Vector3f(), new Vector3f(3,0,0))); attach(source, chosen);
        var id = source.getActorTrackPayload().getActorRef().getActorId();
        assertEquals(1, service.copyRouteFromActor(model, group, id, chosen.getId()));
        assertEquals("Chosen", target.getActorTrackPayload().getClips().getFirst().getName());
        assertTrue(history.undo()); assertTrue(target.getClips().isEmpty());
        source.getActorTrackPayload().removeClip(chosen.getId());
        assertFalse(service.previewRoutes(model, group, id, chosen.getId()).canExecute());
        assertEquals(0, service.copyRouteFromActor(model, group, id, chosen.getId()));
        assertTrue(target.getClips().isEmpty());
    }
    @Test void playheadRouteCopyKeepsDurationAndFreeOffsetsAndChecksShiftedConflicts() {
        var source = member(ActorType.PLAYER); var target = member(ActorType.PLAYER);
        var a = new EntitySpawnClip("A", 0, 30); a.setPosition(1, 2, 3); attach(source, a);
        var b = new EntitySpawnClip("B", 0, 30); b.setPosition(5, 3, 8); attach(target, b);
        var route = new ActorRouteClip("Route", 10, 15); route.setWaypoints(List.of(new Vector3f(1,2,3), new Vector3f(1,2,10))); attach(source, route);
        var id = source.getActorTrackPayload().getActorRef().getActorId();
        var conflict = new EntityTransformClip("Conflict", 3, 4); attach(target, conflict);
        assertFalse(service.previewRoutes(model, group, id, route.getId(), true).canExecute());
        conflict.setMuted(true);
        assertEquals(1, service.copyRouteFromActor(model, group, id, route.getId(), true));
        var copy = target.getActorTrackPayload().getClips().stream().filter(c -> c instanceof ActorRouteClip).map(c -> (ActorRouteClip)c).findFirst().orElseThrow();
        assertEquals(2, copy.getStartTime()); assertEquals(7, copy.getEndTime());
        assertEquals(new Vector3f(5,3,8), copy.getWaypoints().getFirst());
        assertTrue(history.undo()); assertFalse(target.getActorTrackPayload().getClips().contains(copy));
        assertTrue(history.redo()); assertTrue(target.getActorTrackPayload().getClips().contains(copy));
    }
    @Test void groupUndoRejectsReplacedTrackPayloadWithoutChangingData() {
        var track = member(ActorType.PLAYER);
        assertEquals(1, service.createActionClips(model, group, ActorActionType.WALK, 2, 1, true));
        var original = track.getActorTrackPayload(); var replacement = new ActorTrack();
        track.setActorTrackPayload(replacement);
        assertThrows(IllegalArgumentException.class, history::undo);
        assertEquals(1, original.getActionClips().size()); assertTrue(replacement.getClips().isEmpty());
        track.setActorTrackPayload(original); assertTrue(history.undo()); assertTrue(original.getClips().isEmpty());
    }
    @Test void failedGroupUndoRestoresAlreadyUndoneMembers() {
        int[] values = {0,0}; boolean[] fail = {false};
        ActorGroupEditCommand.execute(model, "Undo failure", List.of(
                new ActorGroupEditCommand.Change(() -> {values[0]=0; if(fail[0]) throw new IllegalStateException();}, () -> values[0]=1),
                new ActorGroupEditCommand.Change(() -> values[1]=0, () -> values[1]=1)));
        fail[0]=true; assertThrows(IllegalStateException.class, history::undo);
        assertArrayEquals(new int[]{1,1}, values);
        fail[0]=false; assertTrue(history.undo()); assertArrayEquals(new int[]{0,0}, values);
    }
    @Test void failedMemberRollsBackEvenItsPartialWriteAndDoesNotEnterHistory() {
        int[] values = {0,0};
        assertThrows(IllegalStateException.class, () -> ActorGroupEditCommand.execute(model, "Failure", List.of(
                new ActorGroupEditCommand.Change(() -> values[0]=0, () -> values[0]=1),
                new ActorGroupEditCommand.Change(() -> values[1]=0, () -> {values[1]=1; throw new IllegalStateException();}))));
        assertArrayEquals(new int[]{0,0}, values); assertFalse(history.undo());
    }
    @Test void invalidInputOrAllLockedMakesNoUndoEntry() {
        var track = member(ActorType.PLAYER);
        assertEquals(0,service.createActionClips(model,group,ActorActionType.WALK,Float.NaN,1,true));
        assertEquals(0,service.createActionClips(model,group,ActorActionType.WALK,2,Float.NaN,true));
        track.setLocked(true);
        assertEquals(0,service.createActionClips(model,group,ActorActionType.WALK,2,1,true));
        assertEquals(0,service.placeGroup(model,group,new Vec3d(1,2,3),0,0));
        assertTrue(track.getClips().isEmpty()); assertFalse(history.undo());
    }
}

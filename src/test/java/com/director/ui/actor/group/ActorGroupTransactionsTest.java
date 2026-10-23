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

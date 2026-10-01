package com.director.ui.actor.track;

import com.director.actor.*;
import com.director.actor.Actor;
import com.director.actor.action.ActorActionType;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorTrackClipAuthoringServiceTest {
    private TimelineModel model;
    private TimelineTrack track;
    private TimelineCommandHistory history;
    private final ActorTrackClipAuthoringService service = ActorTrackClipAuthoringService.getInstance();
    @BeforeEach void setup() {
        ActorRegistryManager.setRegistry(null); ActorRegistryManager.setRegistry(new ActorRegistry());
        var actor = new Actor("Player", ActorType.PLAYER); ActorRegistryManager.getInstance().registerActor(actor);
        model = new TimelineModel(new TimelineViewportState()); model.setCurrentTime(3);
        track = new TimelineTrack("Player", TrackType.ACTOR); var payload = new ActorTrack();
        payload.setActorRef(new ActorRef(actor.getActorId())); track.setActorTrackPayload(payload); model.addTrack(track);
        history = new TimelineCommandHistory(); TimelineCommandBridge.bind(history);
    }
    @AfterEach void cleanup() { TimelineCommandBridge.bind(null); ActorRegistryManager.setRegistry(null); }
    @Test void menuCreationIsOneUndoAndRedoRestoresSameClipAndWrapper() {
        var wrapper = service.createClip(model, track, ActorTrackClipKind.POSE, null);
        assertNotNull(wrapper); assertEquals(3, wrapper.getStartTime());
        var clip = track.getActorTrackPayload().getClips().getFirst();
        assertTrue(history.undo()); assertTrue(track.getClips().isEmpty());
        assertTrue(track.getActorTrackPayload().getClips().isEmpty()); assertFalse(history.undo());
        assertFalse(model.isSelected(wrapper)); assertTrue(history.redo());
        assertSame(wrapper, track.getClips().getFirst()); assertSame(clip, track.getActorTrackPayload().getClips().getFirst());
        assertSame(track, wrapper.getParentTrack());
    }
    @Test void lockedDetachedAndPlayingTracksNeverCreateClips() {
        track.setLocked(true); assertNull(service.createClip(model, track, ActorTrackClipKind.STATE, null));
        track.setLocked(false); model.setPlaying(true); assertNull(service.createClip(model, track, ActorTrackClipKind.STATE, null));
        model.setPlaying(false);
        var other = new TimelineModel(new TimelineViewportState()); assertNull(service.createClip(other, track, ActorTrackClipKind.STATE, null));
        assertTrue(track.getClips().isEmpty()); assertTrue(track.getActorTrackPayload().getClips().isEmpty()); assertFalse(history.undo());
    }
    @Test void unsupportedActorActionsAndLegacyPoseAreRejected() {
        var cow = new Actor("Cow", ActorType.COW); ActorRegistryManager.getInstance().registerActor(cow);
        track.getActorTrackPayload().setActorRef(new ActorRef(cow.getActorId()));
        assertNull(service.createClip(model, track, ActorTrackClipKind.POSE, null));
        assertNull(service.createClip(model, track, ActorTrackClipKind.ACTION, ActorActionType.DANCE));
        assertTrue(track.getClips().isEmpty()); assertFalse(history.undo());
        assertNotNull(service.createClip(model, track, ActorTrackClipKind.ACTION, ActorActionType.WALK));
    }
}

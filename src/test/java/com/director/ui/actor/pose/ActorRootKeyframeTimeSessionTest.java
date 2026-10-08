package com.director.ui.actor.pose;

import com.director.actor.*;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.EntitySpawnClip;
import com.director.timeline.model.entity.EntityTimelineClip;
import com.director.timeline.model.entity.EntityClipKeyframe;
import com.director.ui.timeline.commands.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorRootKeyframeTimeSessionTest {
    TimelineModel model; TimelineTrack owner; EntitySpawnClip clip;
    EntityClipKeyframe key, other; TimelineCommandHistory history;
    @BeforeEach void setup() {
        ActorRegistryManager.setRegistry(new ActorRegistry());
        var actor = ActorRegistryManager.getInstance().createActor("A", ActorType.PLAYER);
        model = new TimelineModel(new TimelineViewportState()); model.setSuppressPersistenceWrites(true);
        owner = new TimelineTrack("A", TrackType.ACTOR); owner.getOrCreateActorTrackPayload().setActorRef(ActorRef.of(actor));
        model.addTrack(owner); clip = new EntitySpawnClip("A", 0, 5);
        owner.getActorTrackPayload().addClip(clip); owner.addClip(new EntityTimelineClip(clip, 0));
        key = new EntityClipKeyframe(2); other = new EntityClipKeyframe(4); clip.addKeyframe(key); clip.addKeyframe(other);
        model.setSelectedClip(clip.getTimelineClip()); model.setCurrentTime(2);
        history = new TimelineCommandHistory(); TimelineCommandBridge.bind(history);
    }
    @AfterEach void cleanup() { TimelineCommandBridge.bind(null); ActorRegistryManager.setRegistry(null); }
    ActorRootKeyframeTimeSession session() { return new ActorRootKeyframeTimeSession(model, owner, clip, key); }
    @Test void moveIsOneCommandAndRestoresTimeAndWrapperKeys() {
        var draft = session(); assertEquals(2, key.getTime()); assertEquals(0, history.getUndoSize());
        assertTrue(draft.commit(3)); assertEquals(3, key.getTime()); assertEquals(3, model.getCurrentTime());
        assertEquals(3, clip.getTimelineClip().getClipKeyframes().getFirst().getTime());
        assertEquals(1, history.getUndoSize()); assertEquals(1, owner.getClips().size());
        assertTrue(history.undo()); assertEquals(2, key.getTime()); assertEquals(2, model.getCurrentTime());
        assertTrue(history.redo()); assertEquals(3, key.getTime()); assertEquals(2, clip.getKeyframes().size());
    }
    @Test void collisionAndRangeAreRejectedWithoutReplacingKeys() {
        for (float target : new float[]{4, -1, 6, Float.NaN, Float.POSITIVE_INFINITY, 2}) {
            assertFalse(session().commit(target)); assertEquals(2, key.getTime());
            assertEquals(java.util.List.of(key, other), clip.getKeyframes());
        }
        assertEquals(0, history.getUndoSize());
    }
    @Test void cancelAndExternalKeyChangesInvalidateDraft() {
        var draft = session(); draft.cancel(); assertFalse(draft.commit(3));
        draft = session(); other.setPosition(1, 2, 3); assertFalse(draft.commit(3));
        assertEquals(2, key.getTime()); assertEquals(0, history.getUndoSize());
    }
    @Test void locksSelectionAndScrubbingInvalidateDraft() {
        var draft = session(); owner.setLocked(true); assertFalse(draft.commit(3)); owner.setLocked(false);
        draft = session(); model.setSelectedClip(null); assertFalse(draft.commit(3)); model.setSelectedClip(clip.getTimelineClip());
        draft = session(); model.setCurrentTime(1); model.setCurrentTime(2); assertFalse(draft.commit(3));
        draft = session(); model.setScrubbing(true); model.setScrubbing(false); assertFalse(draft.commit(3));
        assertEquals(0, history.getUndoSize());
    }
    @Test void crossingAKeyKeepsSortedOrderAndUndoIdentity() {
        assertTrue(session().commit(4.5f)); assertEquals(java.util.List.of(other, key), clip.getKeyframes());
        assertTrue(history.undo()); assertEquals(java.util.List.of(key, other), clip.getKeyframes());
    }
}

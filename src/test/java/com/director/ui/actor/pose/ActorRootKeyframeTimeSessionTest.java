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
    @Test void scaleGroupCanUseVacatedScaleTimeWithoutMovingOtherChannels() {
        var session=new ActorRootKeyframeTimeSession(model,owner,clip,key,java.util.List.of(key,other),4);
        assertTrue(session.commit(3));
        assertEquals(2,key.getTime());assertEquals(3,key.getChannels());
        assertEquals(4,other.getTime());assertEquals(3,other.getChannels());
        assertTrue(history.undo());assertEquals(7,key.getChannels());assertEquals(7,other.getChannels());
    }
    @Test void movingScaleSplitsSharedKeyAndUndoRestoresIdentity() {
        key.setPosition(2,3,4);key.setScale(2,2,2);
        var session=new ActorRootKeyframeTimeSession(model,owner,clip,key,java.util.List.of(key),4);
        assertTrue(session.commit(3));
        assertEquals(2,key.getTime());assertEquals(3,key.getChannels());
        var moved=clip.getKeyframes().stream().filter(k->k.getTime()==3).findFirst().orElseThrow();
        assertEquals(4,moved.getChannels());assertEquals(key.getScale(),moved.getScale());
        assertEquals(new org.joml.Vector3f(2,3,4),key.getPosition());
        assertEquals(1,history.getUndoSize());assertTrue(history.undo());
        assertEquals(java.util.List.of(key,other),clip.getKeyframes());assertEquals(7,key.getChannels());
        assertTrue(history.redo());assertTrue(clip.getKeyframes().contains(moved));
    }
    @Test void movingScaleMergesWithOtherChannelsButRejectsScaleCollision() {
        key.setScale(3,4,5);other.setChannels(1);other.setPosition(7,8,9);
        assertTrue(new ActorRootKeyframeTimeSession(model,owner,clip,key,java.util.List.of(key),4).commit(4));
        assertEquals(5,other.getChannels());assertEquals(new org.joml.Vector3f(7,8,9),other.getPosition());
        assertEquals(new org.joml.Vector3f(3,4,5),other.getScale());
        assertTrue(history.undo());assertEquals(1,other.getChannels());assertEquals(7,key.getChannels());
        other.setChannels(4);
        assertFalse(new ActorRootKeyframeTimeSession(model,owner,clip,key,java.util.List.of(key),4).commit(4));
        assertEquals(7,key.getChannels());assertEquals(2,key.getTime());
    }
    @Test void movingChannelsRejectsDifferentInterpolationAtMergeAndStaleMask() {
        other.setChannels(1);other.setInterpolation(TimelineInterpolation.STEP);
        assertFalse(new ActorRootKeyframeTimeSession(model,owner,clip,key,java.util.List.of(key),4).commit(4));
        assertEquals(0,history.getUndoSize());assertEquals(7,key.getChannels());
        var session=new ActorRootKeyframeTimeSession(model,owner,clip,key,java.util.List.of(key),4);
        key.setChannels(1);assertFalse(session.commit(3));
    }

    @Test void groupMoveAllowsVacatedTimesAndRestoresWholeGroupInOneCommand() {
        var draft = new ActorRootKeyframeTimeSession(model, owner, clip, key, java.util.List.of(key, other));
        assertEquals(0, draft.start()); assertEquals(3, draft.end());
        assertEquals(5, draft.previewTime(other, 3)); assertEquals(4, other.getTime());
        assertTrue(draft.commit(3)); assertEquals(3, key.getTime()); assertEquals(5, other.getTime());
        assertEquals(1, history.getUndoSize()); assertTrue(history.undo());
        assertEquals(2, key.getTime()); assertEquals(4, other.getTime()); assertTrue(history.redo());
        assertEquals(3, key.getTime()); assertEquals(5, other.getTime());
    }
    @Test void groupOverflowAndCollisionRejectAllFrames() {
        var stationary = new EntityClipKeyframe(3); clip.addKeyframe(stationary);
        assertFalse(new ActorRootKeyframeTimeSession(model, owner, clip, key, java.util.List.of(key, other)).commit(3));
        assertEquals(2, key.getTime()); assertEquals(4, other.getTime());
        assertFalse(new ActorRootKeyframeTimeSession(model, owner, clip, key, java.util.List.of(key, other)).commit(4));
        assertEquals(0, history.getUndoSize());
        clip.removeKeyframe(stationary.getId());
        assertTrue(new ActorRootKeyframeTimeSession(model, owner, clip, other, java.util.List.of(key, other)).commit(2));
        assertEquals(0, key.getTime()); assertEquals(2, other.getTime());
    }
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

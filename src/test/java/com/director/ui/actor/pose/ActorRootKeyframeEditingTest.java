package com.director.ui.actor.pose;

import com.director.actor.*;
import com.director.actor.Actor;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.timeline.playback.entity.EntityClipSampler;
import com.director.ui.timeline.commands.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorRootKeyframeEditingTest {
    TimelineModel model; TimelineTrack owner; TimelineCommandHistory history;
    @BeforeEach void setup() {
        ActorRegistryManager.setRegistry(new ActorRegistry());
        var actor = ActorRegistryManager.getInstance().createActor("A", ActorType.PLAYER);
        model = new TimelineModel(new TimelineViewportState()); model.setSuppressPersistenceWrites(true);
        owner = new TimelineTrack("A", TrackType.ACTOR);
        owner.getOrCreateActorTrackPayload().setActorRef(ActorRef.of(actor)); model.addTrack(owner);
        history = new TimelineCommandHistory(); TimelineCommandBridge.bind(history);
    }
    @AfterEach void cleanup() { TimelineCommandBridge.bind(null); ActorRegistryManager.setRegistry(null); }
    <T extends EntityClip> T add(T clip) {
        owner.getActorTrackPayload().addClip(clip); owner.addClip(new EntityTimelineClip(clip, 0)); return clip;
    }
    @Test void spawnKeysAnimateInOneClipAndRepeatedTimeReusesKey() {
        var spawn = add(new EntitySpawnClip("Actor", 0, 5));
        spawn.setPosition(1, 2, 3);
        var first = ActorRootKeyframeEditing.insert(model, owner, spawn);
        assertNotNull(first); assertSame(first, ActorRootKeyframeEditing.insert(model, owner, spawn));
        model.setCurrentTime(2);
        var second = ActorRootKeyframeEditing.write(model, owner, spawn,
                new Vector3f(5, 2, 3), new Vector3f(90, 0, 0), new Vector3f(3));
        var sampled = new EntityClipSampler(owner.getActorTrackPayload()).sampleAt(1);
        assertEquals(new Vector3f(3, 2, 3), sampled.position);
        assertEquals(45, sampled.rotation.x); assertEquals(new Vector3f(2), sampled.scale);
        assertTrue(sampled.speed > 0); assertEquals(1, owner.getClips().size());
        assertSame(second, ActorRootKeyframeEditing.insert(model, owner, spawn));
        assertEquals(2, history.getUndoSize());
        assertTrue(history.undo()); assertEquals(1, spawn.getKeyframes().size());
        assertTrue(history.redo()); assertEquals(2, spawn.getKeyframes().size());
    }
    @Test void removingLastSpawnKeyRestoresBaseAndCanUndo() {
        var spawn = add(new EntitySpawnClip("Actor", 0, 5)); spawn.setPosition(1, 2, 3);
        ActorRootKeyframeEditing.write(model, owner, spawn, new Vector3f(8), new Vector3f(), new Vector3f(1));
        assertTrue(ActorRootKeyframeEditing.remove(model, owner, spawn));
        assertEquals(new Vector3f(1, 2, 3), new EntityClipSampler(owner.getActorTrackPayload()).sampleAt(0).position);
        assertTrue(history.undo()); assertEquals(1, spawn.getKeyframes().size());
        assertEquals(new Vector3f(8), new EntityClipSampler(owner.getActorTrackPayload()).sampleAt(0).position);
    }
    @Test void convertingEndpointAnimationKeepsTrajectory() {
        var clip = add(new EntityTransformClip("Move", 0, 4));
        clip.setFromPos(0, 0, 0); clip.setToPos(8, 0, 0); clip.setInterpolation(TimelineInterpolation.LINEAR);
        model.setCurrentTime(2);
        var key = ActorRootKeyframeEditing.insert(model, owner, clip);
        assertEquals(3, clip.getKeyframes().size()); assertEquals(4, key.getPosition().x);
        assertEquals(2, new EntityClipSampler(owner.getActorTrackPayload()).sampleAt(1).position.x);
        assertTrue(history.undo()); assertTrue(clip.getKeyframes().isEmpty());
        assertEquals(2, new EntityClipSampler(owner.getActorTrackPayload()).sampleAt(1).position.x);
    }
    @Test void smoothEndpointConversionCannotSilentlyChangeTrajectory() {
        var clip = add(new EntityTransformClip("Smooth", 0, 4));
        clip.setFromPos(0, 0, 0); clip.setToPos(8, 0, 0); clip.setInterpolation(TimelineInterpolation.SMOOTH);
        model.setCurrentTime(2);
        assertNull(ActorRootKeyframeEditing.insert(model, owner, clip));
        assertTrue(clip.getKeyframes().isEmpty()); assertEquals(0, history.getUndoSize());
        assertEquals(1.25f, new EntityClipSampler(owner.getActorTrackPayload()).sampleAt(1).position.x);
    }
    @Test void readonlyAndOutOfRangeCannotWrite() {
        var spawn = add(new EntitySpawnClip("Actor", 0, 5));
        owner.setLocked(true); assertNull(ActorRootKeyframeEditing.insert(model, owner, spawn));
        owner.setLocked(false); spawn.setMuted(true); assertNull(ActorRootKeyframeEditing.insert(model, owner, spawn));
        spawn.setMuted(false); model.setCurrentTime(6); assertNull(ActorRootKeyframeEditing.insert(model, owner, spawn));
        assertTrue(spawn.getKeyframes().isEmpty()); assertEquals(0, history.getUndoSize());
    }
}

package com.director.ui.actor.pose;

import com.director.actor.*;
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
    @Test void addingAndUndoingSecondActorTrackPreservesFirstTrackAndPayload() {
        var spawn = add(new EntitySpawnClip("First", 0, 5));
        model.setCurrentTime(2);
        var key = ActorRootKeyframeEditing.insertAtPlayhead(model, owner);
        var second = new TimelineTrack("B", TrackType.ACTOR);
        var payload = second.getOrCreateActorTrackPayload();
        history.executeCommand(new AddTrackCommand(model, second));
        assertTrue(model.getAllTracks().contains(second));
        assertTrue(history.undo()); assertFalse(model.getAllTracks().contains(second));
        assertTrue(model.getAllTracks().contains(owner)); assertTrue(spawn.getKeyframes().contains(key));
        assertTrue(history.redo()); assertTrue(model.getAllTracks().contains(second));
        assertSame(payload, second.getActorTrackPayload());
        assertTrue(owner.getAutomationKeyframes().isEmpty());
    }

    @Test void headerWritesSpawnKeyAndUndoWithoutAutomationOrNewClips() {
        var spawn = add(new EntitySpawnClip("Actor", 0, 5));
        model.setCurrentTime(2);
        var key = ActorRootKeyframeEditing.insertAtPlayhead(model, owner);
        assertNotNull(key); assertEquals(2, key.getTime());
        assertTrue(spawn.getKeyframes().contains(key));
        assertTrue(owner.getAutomationKeyframes().isEmpty());
        assertEquals(1, owner.getClips().size());
        assertSame(key, ActorRootKeyframeEditing.insertAtPlayhead(model, owner));
        assertEquals(1, history.getUndoSize());
        assertTrue(history.undo()); assertTrue(spawn.getKeyframes().isEmpty());
        assertTrue(history.redo()); assertTrue(spawn.getKeyframes().contains(key));
    }
    @Test void headerTargetsLatestActiveTransformInsteadOfSpawnOrSelectedFutureClip() {
        var spawn = add(new EntitySpawnClip("Actor", 0, 10));
        var older = add(new EntityTransformClip("Older", 1, 5));
        var current = add(new EntityTransformClip("Current", 2, 5));
        var future = add(new EntityTransformClip("Future", 6, 10));
        model.selectItem(future.getTimelineClip(), false); model.setCurrentTime(2);
        var key = ActorRootKeyframeEditing.insertAtPlayhead(model, owner);
        assertNotNull(key); assertTrue(current.getKeyframes().contains(key));
        assertTrue(spawn.getKeyframes().isEmpty()); assertTrue(older.getKeyframes().isEmpty());
        assertTrue(future.getKeyframes().isEmpty());
    }
    @Test void headerCannotWriteOutsideClipOrWhileLockedOrPlaying() {
        var spawn = add(new EntitySpawnClip("Actor", 0, 5));
        model.setCurrentTime(6); assertNull(ActorRootKeyframeEditing.insertAtPlayhead(model, owner));
        model.setCurrentTime(2); owner.setLocked(true);
        assertNull(ActorRootKeyframeEditing.insertAtPlayhead(model, owner));
        owner.setLocked(false); model.setPlaying(true);
        assertNull(ActorRootKeyframeEditing.insertAtPlayhead(model, owner));
        assertTrue(spawn.getKeyframes().isEmpty()); assertEquals(0, history.getUndoSize());
    }
    @Test void headerDoesNotRequireOneActiveActorTrack() {
        var first = add(new EntitySpawnClip("First", 0, 5));
        var actor = ActorRegistryManager.getInstance().createActor("B", ActorType.PLAYER);
        var second = new TimelineTrack("B", TrackType.ACTOR);
        second.getOrCreateActorTrackPayload().setActorRef(ActorRef.of(actor)); model.addTrack(second);
        var other = new EntitySpawnClip("Second", 0, 5);
        second.getActorTrackPayload().addClip(other); second.addClip(new EntityTimelineClip(other, 0));
        model.setCurrentTime(2);
        assertNotNull(ActorRootKeyframeEditing.insertAtPlayhead(model, owner));
        assertNotNull(ActorRootKeyframeEditing.insertAtPlayhead(model, second));
        assertEquals(1, first.getKeyframes().size()); assertEquals(1, other.getKeyframes().size());
        assertTrue(history.undo()); assertTrue(other.getKeyframes().isEmpty());
        assertEquals(1, first.getKeyframes().size());
    }

    @Test void firstBoneKeyUsesSpawnWithoutExtraClipsAndUndoRemovesResource() {
        var spawn=add(new EntitySpawnClip("Actor",0,5));model.setCurrentTime(2);
        assertTrue(ActorEmbeddedAnimation.firstKey(model,owner,"head",2,com.director.actor.animation.asset.InterpolationMode.SMOOTH));
        var id=spawn.getBoneAnimationId();assertNotNull(id);assertEquals(1,owner.getClips().size());
        assertEquals(1,owner.getActorTrackPayload().getClips().size());assertEquals(1,history.getUndoSize());
        var actorId=owner.getActorTrackPayload().getActorRef().getActorId();
        var context=ActorAnimationEditContext.resolve(model,actorId);assertNotNull(context);assertTrue(context.isCurrent());
        var child=spawn.getBoneAnimation();assertSame(child,context.clip());
        var sample=new EntityClipSampler(owner.getActorTrackPayload()).sampleAt(2);
        assertEquals(id,sample.animationId);assertEquals(2,sample.animationLocalTime);
        var pose=context.asset().sample(2,com.director.actor.animation.asset.AnimationLoopMode.ONCE);
        pose.setLocalTransform("head",pose.localTransform("head").withRotation(new org.joml.Quaternionf().rotateX(.3f)));
        assertTrue(ActorAnimationAuthoringService.keyPose(context,pose,
                java.util.List.of("head"),com.director.actor.animation.asset.InterpolationMode.SMOOTH,2));
        assertTrue(history.undo());
        assertTrue(history.undo());assertNull(spawn.getBoneAnimationId());
        assertFalse(com.director.actor.animation.asset.AnimationAssetLibrary.getInstance().contains(id));
        assertTrue(history.redo());assertSame(child,spawn.getBoneAnimation());
        model.setCurrentTime(3);
        var wrapper=spawn.getTimelineClip();wrapper.setTimeRange(1,6);
        assertEquals(1,spawn.getBoneAnimation().getStartTime());
        var dto=com.director.timeline.persistence.TimelineModelPersistence.toDTO(model,new TimelineViewportState());
        assertEquals(id,dto.tracks.getFirst().actorTrackPayload.clips.getFirst().spawnBoneAnimationId);
        com.director.actor.animation.asset.AnimationAssetLibrary.getInstance().remove(id);
    }
    @Test void movingActorClipMovesAllKeysAndUndoRestoresThem() {
        var spawn=add(new EntitySpawnClip("Actor",0,5));
        var a=new EntityClipKeyframe(1);a.setChannels(1);var b=new EntityClipKeyframe(3);b.setChannels(4);
        spawn.addKeyframe(a);spawn.addKeyframe(b);
        var command=new com.director.ui.timeline.commands.MoveClipCommand(model,new com.director.ui.timeline.commands.ClipAdapter(spawn.getTimelineClip()),2,7);
        TimelineCommandBridge.execute(command);assertEquals(3,a.getTime());assertEquals(5,b.getTime());
        assertTrue(history.undo());assertEquals(1,a.getTime());assertEquals(3,b.getTime());
        assertTrue(history.redo());assertEquals(3,a.getTime());assertEquals(5,b.getTime());
        spawn.getTimelineClip().setTimeRange(0,5);assertEquals(1,a.getTime());assertEquals(3,b.getTime());
    }

    @Test void independentChannelsDoNotInterruptOtherCurvesAndDeleteOnlyOne() {
        var clip=add(new EntitySpawnClip("Actor",0,5));
        ActorRootKeyframeEditing.writeChannel(model,owner,clip,1,new Vector3f(0));
        model.setCurrentTime(4); ActorRootKeyframeEditing.writeChannel(model,owner,clip,1,new Vector3f(8,0,0));
        model.setCurrentTime(0); ActorRootKeyframeEditing.writeChannel(model,owner,clip,4,new Vector3f(1));
        model.setCurrentTime(2); var scaleKey=ActorRootKeyframeEditing.writeChannel(model,owner,clip,4,new Vector3f(3));
        var sampled=new EntityClipSampler(owner.getActorTrackPayload()).sampleAt(1);
        assertEquals(2f,sampled.position.x,1e-6f); assertEquals(new Vector3f(2),sampled.scale);
        assertEquals(4,scaleKey.getChannels());
        var first=clip.getKeyframes().getFirst(); assertEquals(5,first.getChannels());
        assertTrue(ActorRootKeyframeEditing.deleteChannels(model,owner,clip,java.util.List.of(first),4));
        assertEquals(1,first.getChannels()); assertTrue(clip.getKeyframes().contains(first));
        assertTrue(history.undo()); assertEquals(5,first.getChannels());
        var dto=com.director.timeline.persistence.TimelineModelPersistence.toDTO(model,new TimelineViewportState());
        var restored=new TimelineModel(new TimelineViewportState()); restored.setSuppressPersistenceWrites(true);
        com.director.timeline.persistence.TimelineModelPersistence.fromDTO(dto,restored,new TimelineViewportState());
        var loaded=restored.getAllTracks().getFirst().getActorTrackPayload().getClips().getFirst();
        assertEquals(clip.getKeyframes().stream().map(EntityClipKeyframe::getChannels).toList(),loaded.getKeyframes().stream().map(EntityClipKeyframe::getChannels).toList());
    }

    @Test void scaleKeysPreserveRouteAndSpawnPositionAndUndo() {
        var spawn=add(new EntitySpawnClip("A",0,5)); spawn.setPosition(20,2,30);
        var route=add(new ActorRouteClip("Path",0,5));
        route.addWaypoint(0,0,0);
        route.addWaypoint(10,0,0);
        assertNotNull(ActorRootKeyframeEditing.writeScale(model,owner,spawn,new Vector3f(1)));
        model.setCurrentTime(4);
        var key=ActorRootKeyframeEditing.writeScale(model,owner,spawn,new Vector3f(3));
        assertNotNull(key); assertEquals(new Vector3f(20,2,30),key.getPosition());
        var sample=new EntityClipSampler(owner.getActorTrackPayload()).sampleAt(2);
        assertEquals(new Vector3f(2),sample.scale); assertSame(route,sample.routeClip);
        assertNotEquals(new Vector3f(20,2,30),sample.position);
        assertEquals(2,owner.getClips().size());
        assertTrue(history.undo()); assertEquals(1,spawn.getKeyframes().size());
        assertTrue(history.redo()); assertEquals(2,spawn.getKeyframes().size());
        owner.setLocked(true); assertNull(ActorRootKeyframeEditing.writeScale(model,owner,spawn,new Vector3f(2)));
    }

    @Test void clipboardIsDetachedAndPasteIsUndoableWithoutNewReferences() {
        var clip = add(new EntitySpawnClip("A", 0, 5));
        var source = new EntityClipKeyframe(1, TimelineInterpolation.STEP);
        source.setPosition(3, 4, 5); source.setDisplayName("Source"); clip.addKeyframe(source);
        var clipboard = new ActorRootKeyframeClipboard(); assertTrue(clipboard.copy(clip, source));
        source.setPosition(9, 9, 9); model.setCurrentTime(3);
        var pasted = clipboard.paste(model, owner, clip); assertNotNull(pasted);
        assertNotEquals(source.getId(), pasted.getId());
        assertEquals(new Vector3f(3, 4, 5), pasted.getPosition());
        assertEquals("Source", pasted.getDisplayName()); assertEquals(TimelineInterpolation.STEP, pasted.getInterpolation());
        assertEquals(1, owner.getClips().size()); assertEquals(1, history.getUndoSize());
        assertNull(clipboard.paste(model, owner, clip)); assertEquals(1, history.getUndoSize());
        assertTrue(history.undo()); assertEquals(java.util.List.of(source), clip.getKeyframes());
        assertTrue(history.redo()); assertTrue(clip.getKeyframes().contains(pasted));
        model.setCurrentTime(4); owner.setLocked(true); assertNull(clipboard.paste(model, owner, clip));
        owner.setLocked(false); model.setCurrentTime(6); assertNull(clipboard.paste(model, owner, clip));
    }
    @Test void clipboardDoesNotSilentlyConvertAnEmptyTransform() {
        var sourceClip = add(new EntitySpawnClip("A", 0, 5));
        var key = new EntityClipKeyframe(1); sourceClip.addKeyframe(key);
        var clipboard = new ActorRootKeyframeClipboard(); assertTrue(clipboard.copy(sourceClip, key));
        var target = add(new EntityTransformClip("Move", 0, 5)); model.setCurrentTime(2);
        assertNull(clipboard.paste(model, owner, target)); assertEquals(0, history.getUndoSize());
        assertTrue(target.getKeyframes().isEmpty());
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
        assertEquals(3, clip.getKeyframes().size());
        assertNotNull(key);
        assertEquals(4, key.getPosition().x);
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

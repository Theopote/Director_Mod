package com.director.ui.actor.pose;

import com.director.actor.*;
import com.director.actor.Actor;
import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import org.junit.jupiter.api.*;
import org.joml.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorPoseGalleryEditingTest {
    private TimelineModel model;
    private TimelineTrack track;
    private Actor actor;
    private EntitySpawnClip spawn;
    private TimelineCommandHistory history;
    private final List<String> assets=new ArrayList<>();
    @BeforeEach void setup() {
        ActorRegistryManager.setRegistry(null);ActorRegistryManager.setRegistry(new ActorRegistry());
        actor=new Actor("Player",ActorType.PLAYER);ActorRegistryManager.getInstance().registerActor(actor);
        model=new TimelineModel(new TimelineViewportState());model.setCurrentTime(1);
        track=new TimelineTrack("Player",TrackType.ACTOR);
        var payload=new ActorTrack();payload.setActorRef(new ActorRef(actor.getActorId()));
        track.setActorTrackPayload(payload);model.addTrack(track);
        spawn=new EntitySpawnClip("Player",0,5);payload.addClip(spawn);track.addClip(new EntityTimelineClip(spawn,payload.getColorRgba()));
        history=new TimelineCommandHistory();TimelineCommandBridge.bind(history);
    }
    @AfterEach void cleanup() {
        if(spawn.getBoneAnimationId()!=null) assets.add(spawn.getBoneAnimationId());
        assets.forEach(AnimationAssetLibrary.getInstance()::remove);
        TimelineCommandBridge.bind(null);ActorRegistryManager.setRegistry(null);
    }
    private boolean pose(String... bones) {
        var selected=List.of(bones);
        return ActorPoseGalleryEditing.apply(model,actor.getActorId(),selected,
                p->ActorPosePresets.apply(p,ActorPosePresets.Preset.RAISE_HAND,selected));
    }
    @Test void firstPoseCreatesOnlyRotationChannelsAndUndoesAsOneOperation() {
        assertTrue(pose(HumanoidRig.RIGHT_ARM));
        String id=spawn.getBoneAnimationId();assets.add(id);
        var asset=AnimationAssetLibrary.getInstance().find(id).orElseThrow();
        assertEquals(1,asset.boneTracks().size());
        var bone=asset.findBoneTrack(HumanoidRig.RIGHT_ARM).orElseThrow();
        assertTrue(bone.translation().keyframes().isEmpty());assertTrue(bone.scale().keyframes().isEmpty());
        assertTrue(bone.rotation().findAt(1).isPresent());
        assertEquals(1,track.getClips().size());
        assertTrue(history.undo());assertNull(spawn.getBoneAnimationId());
        assertFalse(AnimationAssetLibrary.getInstance().contains(id));assertFalse(history.undo());
        assertTrue(history.redo());assertSame(asset,AnimationAssetLibrary.getInstance().find(id).orElseThrow());
        assertEquals(id,spawn.getBoneAnimationId());
    }
    @Test void existingPosePreservesPositionScaleAndUnselectedBones() {
        assertTrue(pose(HumanoidRig.RIGHT_ARM));
        var asset=AnimationAssetLibrary.getInstance().find(spawn.getBoneAnimationId()).orElseThrow();
        var right=asset.findBoneTrack(HumanoidRig.RIGHT_ARM).orElseThrow();
        right.scale().put(1,new Vector3f(2),InterpolationMode.LINEAR);
        asset.getOrCreateBoneTrack(HumanoidRig.ROOT).translation().put(1,new Vector3f(1,2,3),InterpolationMode.LINEAR);
        assertTrue(ActorPoseGalleryEditing.apply(model,actor.getActorId(),List.of(HumanoidRig.LEFT_ARM),
                p->ActorPosePresets.apply(p,ActorPosePresets.Preset.T_POSE,List.of(HumanoidRig.LEFT_ARM))));
        assertEquals(new Vector3f(2),right.scale().findAt(1).orElseThrow().value());
        assertEquals(new Vector3f(1,2,3),asset.findBoneTrack(HumanoidRig.ROOT).orElseThrow().translation().findAt(1).orElseThrow().value());
        assertTrue(history.undo());assertTrue(asset.findBoneTrack(HumanoidRig.LEFT_ARM).isEmpty());
        assertTrue(right.rotation().findAt(1).isPresent());
    }
    @Test void lockedMutedPlayingAndOutsideClipCannotCreateAnAsset() {
        track.setLocked(true);assertFalse(pose(HumanoidRig.RIGHT_ARM));track.setLocked(false);
        track.setMuted(true);assertFalse(pose(HumanoidRig.RIGHT_ARM));track.setMuted(false);
        model.setPlaying(true);assertFalse(pose(HumanoidRig.RIGHT_ARM));model.setPlaying(false);
        model.setCurrentTime(6);assertFalse(pose(HumanoidRig.RIGHT_ARM));
        assertNull(spawn.getBoneAnimationId());assertFalse(history.undo());
    }
    @Test void emptyOrUnsupportedSelectionDoesNotCreateHistory() {
        assertFalse(pose());assertFalse(pose(HumanoidRig.ROOT,"missing"));
        assertNull(spawn.getBoneAnimationId());assertFalse(history.undo());
    }
    @Test void bindPreservesCorrectionLayerAndUndoRestoresPreviousBinding() {
        assertTrue(pose(HumanoidRig.RIGHT_ARM));String correction=spawn.getBoneAnimationId();
        var before=new ActorPresetBinding("idle",true,1,PoseBlendSettings.DEFAULT);spawn.setPreset(before);
        assertTrue(ActorPoseGalleryEditing.bind(model,actor.getActorId(),spawn,"wave"));
        assertEquals("wave",spawn.getPreset().animationId());assertEquals(correction,spawn.getBoneAnimationId());
        assertTrue(history.undo());assertEquals(before,spawn.getPreset());assertEquals(correction,spawn.getBoneAnimationId());
        assertTrue(history.redo());assertEquals("wave",spawn.getPreset().animationId());
        assertFalse(ActorPoseGalleryEditing.bind(model,actor.getActorId(),spawn,correction));
        assertFalse(ActorPoseGalleryEditing.bind(model,actor.getActorId(),spawn,"missing:animation"));
    }
    @Test void savedPartialPoseWritesOnlyItsSelectedRotation() {
        var rig=ArticulatedPlayerRig.DEFINITION;
        var pose=new RigPose(rig);pose.setLocalTransform(HumanoidRig.HEAD,pose.localTransform(HumanoidRig.HEAD).withRotation(new Quaternionf().rotateY(.6f)));
        var saved=ActorSavedPoseLibrary.capture("Look",pose,List.of(HumanoidRig.HEAD));
        assertTrue(ActorPoseGalleryEditing.apply(model,actor.getActorId(),List.of(HumanoidRig.HEAD),p->saved.apply(p,List.of(HumanoidRig.HEAD))));
        var asset=AnimationAssetLibrary.getInstance().find(spawn.getBoneAnimationId()).orElseThrow();
        assertEquals(Set.of(HumanoidRig.HEAD),asset.boneTracks().keySet());
    }
}

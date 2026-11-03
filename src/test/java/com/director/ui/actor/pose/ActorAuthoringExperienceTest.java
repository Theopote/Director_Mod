package com.director.ui.actor.pose;

import com.director.actor.*;
import com.director.actor.Actor;
import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.timeline.playback.entity.EntityActionEvent;
import com.director.ui.input.ShortcutAction;
import com.director.ui.input.ShortcutBindings;
import com.director.ui.timeline.commands.*;
import org.joml.Quaternionf;
import org.junit.jupiter.api.*;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorAuthoringExperienceTest {
    private TimelineModel model;
    private TimelineTrack owner;
    private TimelineCommandHistory history;
    private final Set<String> assets=new HashSet<>();

    @BeforeEach void setup() {
        ActorRegistryManager.setRegistry(new ActorRegistry());
        Actor actor=ActorRegistryManager.getInstance().createActor("Actor",ActorType.PLAYER);
        model=new TimelineModel(new TimelineViewportState());model.setSuppressPersistenceWrites(true);
        owner=new TimelineTrack("Actor",TrackType.ACTOR);
        owner.getOrCreateActorTrackPayload().setActorRef(ActorRef.of(actor));model.addTrack(owner);
        history=new TimelineCommandHistory();TimelineCommandBridge.bind(history);
    }
    @AfterEach void cleanup() {
        assets.forEach(id->AnimationAssetLibrary.getInstance().remove(id));
        TimelineCommandBridge.bind(null);ActorRegistryManager.setRegistry(null);
    }
    private <T extends EntityClip> T add(T clip) {
        owner.getActorTrackPayload().addClip(clip);owner.addClip(new EntityTimelineClip(clip,0));return clip;
    }
    private AnimationAsset asset() {
        String id="test:experience:"+UUID.randomUUID();assets.add(id);
        var asset=new AnimationAsset(id,"Animation",ArticulatedPlayerRig.DEFINITION,1,AnimationLoopMode.LOOP);
        AnimationAssetLibrary.getInstance().register(asset);return asset;
    }

    @Test void browsingEmptyChannelsDoesNotCreateTracksOrUndoEntries() {
        var asset=asset();var rows=ActorAnimationDopeSheet.rows(asset);
        assertTrue(rows.stream().anyMatch(r->r.boneId().equals("head") && r.channel()==ActorAnimationDopeSheet.Channel.ROTATION));
        assertFalse(rows.stream().anyMatch(r->r.boneId().equals("root") && r.channel()==ActorAnimationDopeSheet.Channel.ROTATION));
        assertEquals(rows,ActorAnimationDopeSheet.rows(asset));
        assertTrue(asset.boneTracks().isEmpty());assertEquals(0,history.getUndoSize());
    }

    @Test void firstKeysForMultipleBonesCaptureVisiblePoseInOneUndo() {
        var spawn=add(new EntitySpawnClip("Spawn",0,5));model.setCurrentTime(2);
        var visible=new RigPose(ArticulatedPlayerRig.DEFINITION);
        var rotation=new Quaternionf().rotateX(.7f);
        visible.setLocalTransform("head",visible.localTransform("head").withRotation(rotation));
        assertTrue(ActorEmbeddedAnimation.firstKeys(model,owner,List.of("head","right_arm"),RigPose.ALL_CHANNELS,InterpolationMode.LINEAR,visible));
        String id=spawn.getBoneAnimationId();assets.add(id);
        var asset=AnimationAssetLibrary.getInstance().find(id).orElseThrow();
        assertEquals(rotation,asset.sample(2).localTransform("head").rotation());
        assertEquals(2,asset.boneTracks().size());assertEquals(1,history.getUndoSize());
        assertTrue(history.undo());assertNull(spawn.getBoneAnimationId());assertFalse(AnimationAssetLibrary.getInstance().contains(id));
        assertFalse(history.undo());assertTrue(history.redo());assertEquals(id,spawn.getBoneAnimationId());
        assertEquals(rotation,asset.sample(2).localTransform("head").rotation());
    }

    @Test void lockedTrackAndInvalidSelectionCannotCreateFirstKeys() {
        var spawn=add(new EntitySpawnClip("Spawn",0,5));model.setCurrentTime(2);owner.setLocked(true);
        assertFalse(ActorEmbeddedAnimation.firstKeys(model,owner,List.of("head"),7,InterpolationMode.LINEAR,null));
        owner.setLocked(false);
        assertFalse(ActorEmbeddedAnimation.firstKeys(model,owner,List.of("head","missing"),7,InterpolationMode.LINEAR,null));
        assertNull(spawn.getBoneAnimationId());assertEquals(0,history.getUndoSize());
    }

    @Test void eventStripUsesDistinctClipAndLoopingSourceClocks() {
        var asset=asset();var clip=add(new EntityAnimationClip("Animation",1,5));clip.setAnimationId(asset.id());clip.setLoop(true);
        var action=new EntityActionEvent(EntityActionClip.ActionType.DETACH_PROP,Map.of("slot","MAIN_HAND"));
        var source=new AnimationEvent(.25f,action,AnimationEvent.Timing.ANIMATION);
        clip.addEvent(source);clip.addEvent(new AnimationEvent(.5f,action,AnimationEvent.Timing.CLIP));
        assertEquals(List.of(1.25f,2.25f,3f,3.25f,4.25f),ActorAnimationEventTimeline.occurrences(owner,clip,1,5).stream().map(ActorAnimationEventTimeline.Occurrence::time).toList());
        model.setCurrentTime(3.8f);assertTrue(ActorAnimationEventTimeline.seek(model,owner,clip,source));
        assertEquals(4.25f,model.getCurrentTime());assertEquals(0,history.getUndoSize());
    }

    @Test void keyNavigationIsStrictAndDoesNotWrapOrAlterKeys() {
        model.setCurrentTime(2);
        var times=List.of(1f,2f,3f,3f,Float.NaN);
        assertTrue(ActorPoseKeyActions.seek(model,times,true));assertEquals(3,model.getCurrentTime());
        assertFalse(ActorPoseKeyActions.seek(model,times,true));
        assertTrue(ActorPoseKeyActions.seek(model,times,false));assertEquals(2,model.getCurrentTime());
        assertEquals(0,history.getUndoSize());
    }

    @Test void unreachableKeyReportsFailureAndRetainsThePlayhead() {
        var asset=asset();var clip=add(new EntityAnimationClip("Animation",1,1.2f));
        clip.setAnimationId(asset.id());clip.setLoop(false);model.setCurrentTime(1.1f);
        assertFalse(ActorAnimationKeyClock.seek(model,owner,clip,.8f));
        assertEquals(1.1f,model.getCurrentTime());
        assertTrue(ActorAnimationKeyClock.seek(model,owner,clip,.1f));
        assertEquals(1.1f,model.getCurrentTime(),1e-5f);
    }

    @Test void actorShortcutsHaveDistinctBindingsAndDoNotUseBareKeys() {
        assertEquals(ShortcutAction.ACTOR_KEY_POSE,ShortcutBindings.getAction(GLFW.GLFW_KEY_Q,true,false,false));
        assertEquals(ShortcutAction.ACTOR_KEY_PREVIOUS,ShortcutBindings.getAction(GLFW.GLFW_KEY_LEFT_BRACKET,true,false,false));
        assertEquals(ShortcutAction.ACTOR_KEY_NEXT,ShortcutBindings.getAction(GLFW.GLFW_KEY_RIGHT_BRACKET,true,false,false));
        assertNull(ShortcutBindings.getAction(GLFW.GLFW_KEY_Q,false,false,false));
    }

    @Test void snappingKeepsGroupOffsetsAndPrefersNearbySceneTargets() {
        var keys=List.of(1f,2f);
        assertEquals(.25f,ActorKeyframeSnapping.delta(24,100,.25f,keys,List.of()),1e-6f);
        assertEquals(.24f,ActorKeyframeSnapping.delta(24,100,0,keys,List.of()),1e-6f);
        assertEquals(.3f,ActorKeyframeSnapping.delta(24,100,.25f,keys,List.of(2.3f)),1e-6f);
        assertEquals(.25f,ActorKeyframeSnapping.delta(24,100,.25f,keys,List.of(5f)),1e-6f);
        assertEquals(-.3f,ActorKeyframeSnapping.delta(-28,100,.25f,keys,List.of(.7f)),1e-6f);
    }
}

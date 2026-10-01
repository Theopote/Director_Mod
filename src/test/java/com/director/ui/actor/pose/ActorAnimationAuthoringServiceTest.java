package com.director.ui.actor.pose;

import com.director.actor.*;
import com.director.actor.Actor;
import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import org.joml.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.director.ui.actor.pose.ActorAnimationAuthoringService.Channel.*;

class ActorAnimationAuthoringServiceTest {
    private TimelineModel model;
    private TimelineTrack track;
    private Actor actor;
    private EntityAnimationClip clip;
    private AnimationAsset asset;
    private TimelineCommandHistory history;
    private ActorAnimationEditContext context;
    @BeforeEach void setup() {
        ActorRegistryManager.setRegistry(null); ActorRegistryManager.setRegistry(new ActorRegistry());
        actor = new Actor("Player", ActorType.PLAYER); ActorRegistryManager.getInstance().registerActor(actor);
        model = new TimelineModel(new TimelineViewportState()); model.setCurrentTime(1);
        track = new TimelineTrack("Player", TrackType.ACTOR);
        ActorTrack payload = new ActorTrack(); payload.setActorRef(new ActorRef(actor.getActorId()));
        track.setActorTrackPayload(payload); model.addTrack(track);
        asset = new AnimationAsset("test:authoring", "Animation", HumanoidRig.DEFINITION, 5, AnimationLoopMode.ONCE);
        AnimationAssetLibrary.getInstance().register(asset);
        clip = new EntityAnimationClip("Animation", 0, 15); clip.setAnimationId(asset.id());
        payload.addClip(clip); track.addClip(new EntityTimelineClip(clip, payload.getColorRgba()));
        history = new TimelineCommandHistory(); TimelineCommandBridge.bind(history);
        context = ActorAnimationEditContext.resolve(model, actor.getActorId());
        assertNotNull(context); assertTrue(context.isCurrent());
    }
    @AfterEach void cleanup() {
        TimelineCommandBridge.bind(null); ActorRegistryManager.setRegistry(null);
        AnimationAssetLibrary.getInstance().remove("test:authoring");
    }
    private ActorAnimationAuthoringService.Key key(String bone, ActorAnimationAuthoringService.Channel channel, float time) {
        return new ActorAnimationAuthoringService.Key(bone, channel, time);
    }
    private ActorAnimationAuthoringService.Paste paste(String bone, ActorAnimationAuthoringService.Channel channel, float time, Object value) {
        return new ActorAnimationAuthoringService.Paste(bone, channel, time, value, InterpolationMode.LINEAR);
    }
    private List<ActorAnimationAuthoringService.Key> seedMixed() {
        asset.getOrCreateBoneTrack("root").translation().put(1, new Vector3f(1,2,3), InterpolationMode.STEP);
        asset.getOrCreateBoneTrack("head").rotation().put(1, new Quaternionf().rotateX(.3f), InterpolationMode.SMOOTH);
        asset.getOrCreateBoneTrack("head").scale().put(1, new Vector3f(2), InterpolationMode.LINEAR);
        return List.of(key("root", TRANSLATION, 1), key("head", ROTATION, 1), key("head", SCALE, 1));
    }
    @Test void mixedMoveUsesOneUndoAndPreservesValuesAndInterpolation() {
        var keys = seedMixed(); var head = asset.findBoneTrack("head").orElseThrow();
        assertTrue(ActorAnimationAuthoringService.move(context, keys, .5f));
        assertTrue(head.rotation().findAt(1).isEmpty()); assertTrue(head.scale().findAt(1.5f).isPresent());
        assertEquals(InterpolationMode.STEP, asset.findBoneTrack("root").orElseThrow().translation().findAt(1.5f).orElseThrow().interpolation());
        assertTrue(history.undo()); assertFalse(history.undo());
        assertEquals(new Vector3f(2), head.scale().findAt(1).orElseThrow().value());
        assertTrue(history.redo()); assertSame(head, asset.findBoneTrack("head").orElseThrow());
    }
    @Test void collisionInOneChannelRejectsTheWholeMixedMove() {
        var keys = seedMixed(); asset.getOrCreateBoneTrack("head").scale().put(2, new Vector3f(3), InterpolationMode.LINEAR);
        assertFalse(ActorAnimationAuthoringService.move(context, keys, 1));
        assertTrue(asset.findBoneTrack("root").orElseThrow().translation().findAt(1).isPresent());
        assertTrue(asset.findBoneTrack("head").orElseThrow().rotation().findAt(1).isPresent());
        assertFalse(history.undo());
    }
    @Test void mixedDeleteRestoresEveryChannelWithOneUndo() {
        var keys = seedMixed(); assertTrue(ActorAnimationAuthoringService.delete(context, keys));
        assertTrue(asset.findBoneTrack("root").orElseThrow().isEmpty());
        assertTrue(asset.findBoneTrack("head").orElseThrow().isEmpty());
        assertTrue(history.undo()); assertFalse(history.undo());
        assertTrue(asset.findBoneTrack("head").orElseThrow().scale().findAt(1).isPresent());
        assertTrue(history.redo()); assertTrue(asset.findBoneTrack("head").orElseThrow().isEmpty());
    }
    @Test void mixedPasteCreatesTracksOnlyOnCommitAndUndoRemovesThem() {
        var pasted = List.of(paste("root", TRANSLATION, 2, new Vector3f(1)),
                paste("head", ROTATION, 2, new Quaternionf()), paste("head", SCALE, 2, new Vector3f(2)));
        assertTrue(ActorAnimationAuthoringService.paste(context, pasted)); assertEquals(2, asset.boneTracks().size());
        assertTrue(history.undo()); assertTrue(asset.boneTracks().isEmpty()); assertFalse(history.undo());
        assertTrue(history.redo()); assertEquals(2, asset.boneTracks().size());
        assertTrue(asset.findBoneTrack("head").orElseThrow().scale().findAt(2).isPresent());
    }
    @Test void failedPasteLeavesNoTracksOrUndoEntry() {
        var valid = paste("head", ROTATION, 2, new Quaternionf());
        assertFalse(ActorAnimationAuthoringService.paste(context, List.of(valid, paste("missing", SCALE, 2, new Vector3f(1)))));
        assertTrue(asset.boneTracks().isEmpty()); assertFalse(history.undo());
        assertFalse(ActorAnimationAuthoringService.paste(context, List.of(valid, paste("root", TRANSLATION, 7, new Vector3f()))));
        assertTrue(asset.boneTracks().isEmpty());
        assertFalse(ActorAnimationAuthoringService.paste(context, List.of(valid, paste("body", SCALE, 2, new Vector3f(0)))));
        assertFalse(ActorAnimationAuthoringService.paste(context, List.of(valid, paste("body", SCALE, 2, new Vector3f(Float.NaN)))));
        assertTrue(asset.boneTracks().isEmpty());
    }
    @Test void approximateDuplicatePasteTargetsAreRejectedBeforeMutation() {
        assertFalse(ActorAnimationAuthoringService.paste(context, List.of(
                paste("head", ROTATION, 2, new Quaternionf()), paste("head", ROTATION, 2.000001f, new Quaternionf()))));
        assertTrue(asset.boneTracks().isEmpty()); assertFalse(history.undo());
    }
    @Test void selectedKeysCanMoveThroughEachOthersOriginalTimes() {
        var head = asset.getOrCreateBoneTrack("head");
        head.rotation().put(1, new Quaternionf().rotateX(.1f), InterpolationMode.STEP);
        head.rotation().put(2, new Quaternionf().rotateY(.2f), InterpolationMode.LINEAR);
        assertTrue(ActorAnimationAuthoringService.move(context, List.of(key("head", ROTATION, 1), key("head", ROTATION, 2)), 1));
        assertEquals(InterpolationMode.STEP, head.rotation().findAt(2).orElseThrow().interpolation());
        assertEquals(InterpolationMode.LINEAR, head.rotation().findAt(3).orElseThrow().interpolation());
        assertTrue(history.undo()); assertTrue(head.rotation().findAt(1).isPresent()); assertTrue(head.rotation().findAt(3).isEmpty());
    }
    @Test void locksPlaybackAndStaleContextsRejectNumericKeying() {
        var pose = new RigPose(HumanoidRig.DEFINITION);
        track.setLocked(true); assertFalse(ActorAnimationAuthoringService.keyPose(context, pose, List.of("head"), InterpolationMode.LINEAR, RigPose.ROTATION));
        track.setLocked(false); model.setPlaying(true); assertFalse(context.isCurrent());
        model.setPlaying(false); track.setMuted(true); assertFalse(context.isCurrent()); track.setMuted(false);
        model.setCurrentTime(2); assertFalse(context.isCurrent()); model.setCurrentTime(1);
        clip.setSpeed(2); assertFalse(context.isCurrent()); clip.setSpeed(1);
        clip.setMuted(true); assertFalse(context.isCurrent()); clip.setMuted(false);
        clip.setTimeRange(0, 10); assertFalse(context.isCurrent()); clip.setTimeRange(0, 15);
        track.getActorTrackPayload().removeClip(clip.getId()); assertFalse(context.isCurrent());
        assertTrue(asset.boneTracks().isEmpty()); assertFalse(history.undo());
    }
    @Test void assetReplacementAndActorRebindingInvalidateCapturedTarget() {
        var replacement = new AnimationAsset(asset.id(), "Replacement", HumanoidRig.DEFINITION, 5, AnimationLoopMode.ONCE);
        AnimationAssetLibrary.getInstance().register(replacement); assertFalse(context.isCurrent());
        AnimationAssetLibrary.getInstance().register(asset); assertTrue(context.isCurrent());
        track.getActorTrackPayload().setActorRef(new ActorRef(UUID.randomUUID())); assertFalse(context.isCurrent());
    }
    @Test void repeatedNumericKeyingIsANoOpAndUndoPreservesUntouchedChannels() {
        seedMixed(); var pose = asset.sample(1, AnimationLoopMode.ONCE);
        var original = asset.findBoneTrack("head").orElseThrow().rotation().findAt(1).orElseThrow();
        pose.setLocalTransform("head", pose.localTransform("head").withRotation(new Quaternionf().rotateY(.5f)));
        assertTrue(ActorAnimationAuthoringService.keyPose(context, pose, List.of("head"), InterpolationMode.LINEAR, RigPose.ROTATION));
        assertFalse(ActorAnimationAuthoringService.keyPose(context, pose, List.of("head"), InterpolationMode.LINEAR, RigPose.ROTATION));
        assertTrue(history.undo()); assertFalse(history.undo());
        assertEquals(original, asset.findBoneTrack("head").orElseThrow().rotation().findAt(1).orElseThrow());
        assertEquals(new Vector3f(2), asset.findBoneTrack("head").orElseThrow().scale().findAt(1).orElseThrow().value());
    }
    @Test void loopingClipKeysAtNormalizedAssetTime() {
        clip.setLoop(true); asset.setLoopMode(AnimationLoopMode.LOOP); model.setCurrentTime(7);
        context = ActorAnimationEditContext.resolve(model, actor.getActorId()); assertEquals(2, context.assetTime());
        assertTrue(ActorAnimationAuthoringService.keyPose(context, new RigPose(HumanoidRig.DEFINITION), List.of("head"), InterpolationMode.LINEAR, RigPose.ROTATION));
        assertTrue(asset.findBoneTrack("head").orElseThrow().rotation().findAt(2).isPresent());
    }
    @Test void mirrorUndoRemovesNewTracksAndPreservesOriginalSparseKeys() {
        asset.getOrCreateBoneTrack("left_arm").rotation().put(1, new Quaternionf().rotateZ(.3f), InterpolationMode.STEP);
        assertTrue(ActorAnimationAuthoringService.mirror(context, List.of(1f), InterpolationMode.LINEAR));
        assertEquals(6, asset.boneTracks().size()); assertTrue(history.undo());
        assertEquals(1, asset.boneTracks().size()); assertFalse(history.undo());
        assertTrue(history.redo()); assertEquals(6, asset.boneTracks().size());
    }
}

package com.director.ui.actor.pose;
import com.director.actor.*;
import com.director.actor.animation.asset.*;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.ui.timeline.commands.*;
import org.joml.Quaternionf;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorBoneGestureOwnershipTest {
    private final TimelineModel model=new TimelineModel(new TimelineViewportState());
    private final TimelineTrack owner=new TimelineTrack("Actor",TrackType.ACTOR);
    private final EntitySpawnClip spawn=new EntitySpawnClip("Spawn",3,9);
    private final TimelineCommandHistory history=new TimelineCommandHistory();
    private ActorBoneGizmoController controller;
    private AnimationAsset asset;
    @BeforeEach void setup() throws Exception {
        ActorRegistryManager.setRegistry(new ActorRegistry());
        var actor=ActorRegistryManager.getInstance().createActor("Player",ActorType.PLAYER);
        model.addTrack(owner);model.setCurrentTime(5);
        owner.getOrCreateActorTrackPayload().setActorRef(ActorRef.of(actor));
        owner.getActorTrackPayload().addClip(spawn);owner.addClip(new EntityTimelineClip(spawn,0));
        var constructor=ActorBoneGizmoController.class.getDeclaredConstructor();constructor.setAccessible(true);
        controller=constructor.newInstance();
        set("actorId",actor.getActorId());set("boneId","head");set("timelineTrack",owner);
        set("actorTrack",owner.getActorTrackPayload());set("gestureInterpolation",InterpolationMode.LINEAR);
        TimelineCommandBridge.bind(history);
    }
    private void set(String name,Object value) throws Exception {var f=controller.getClass().getDeclaredField(name);f.setAccessible(true);f.set(controller,value);}
    private Object get(String name) throws Exception {var f=controller.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(controller);}
    private void invoke(String name,Class<?>[] types,Object... args) throws Exception {var m=controller.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(controller,args);}
    private void begin() throws Exception {
        invoke("beginAnimationGesture",new Class[]{TimelineModel.class,float.class},model,5f);
        asset=(AnimationAsset)get("editingAsset");assertNotNull(asset);
    }
    @AfterEach void cleanup() {
        if(asset!=null) AnimationAssetLibrary.getInstance().remove(asset.id());
        TimelineCommandBridge.bind(null);ActorRegistryManager.setRegistry(null);
    }
    @Test void firstGizmoKeyBelongsToSpawnAndUndoRedoNeverCreatesVisibleClips() throws Exception {
        begin();
        assertNull(spawn.getBoneAnimationId());assertFalse(AnimationAssetLibrary.getInstance().contains(asset.id()));
        assertEquals(6,asset.duration());assertEquals(2f,get("animationKeyTime"));
        ((BoneTrack)get("editingBoneTrack")).rotation().put(2,new Quaternionf().rotateX(.5f),InterpolationMode.LINEAR);
        invoke("registerAnimationUndo",new Class[]{TimelineModel.class},model);
        assertEquals(asset.id(),spawn.getBoneAnimationId());assertEquals(1,owner.getClips().size());
        assertEquals(1,owner.getActorTrackPayload().getClips().size());
        assertTrue(asset.findBoneTrack("head").orElseThrow().rotation().findAt(2).isPresent());
        assertTrue(history.undo());assertNull(spawn.getBoneAnimationId());assertFalse(history.undo());
        assertFalse(AnimationAssetLibrary.getInstance().contains(asset.id()));assertEquals(1,owner.getClips().size());
        assertTrue(history.redo());assertEquals(asset.id(),spawn.getBoneAnimationId());assertEquals(1,owner.getClips().size());
        invoke("resetAnimationGesture",new Class[]{});begin();
        assertSame(asset,AnimationAssetLibrary.getInstance().find(spawn.getBoneAnimationId()).orElseThrow());
        assertEquals(false,get("createdAnimationBinding"));
    }
    @Test void abandoningFirstGestureLeavesNoAnimationOrExtraClip() throws Exception {
        begin();invoke("clearDraft",new Class[]{});
        assertNull(spawn.getBoneAnimationId());assertFalse(AnimationAssetLibrary.getInstance().contains(asset.id()));
        assertEquals(1,owner.getClips().size());assertFalse(history.undo());
    }
    @Test void firstGestureInheritsSpawnMixAndRejectsAChangedBlendConfiguration() throws Exception {
        var settings=new com.director.actor.rig.skeleton.PoseBlendSettings(
                com.director.actor.rig.skeleton.PoseLayer.BlendMode.ADDITIVE,.3f,2,true,java.util.Set.of(),.2f,.4f);
        spawn.setBlendSettings(settings);begin();
        var clip=(EntityAnimationClip)get("editingAnimationClip");assertEquals(settings,clip.getBlendSettings());
        var method=controller.getClass().getDeclaredMethod("animationSourceUnchanged");method.setAccessible(true);
        assertEquals(true,method.invoke(controller));
        spawn.setBlendSettings(com.director.actor.rig.skeleton.PoseBlendSettings.DEFAULT);
        assertEquals(false,method.invoke(controller));
        assertNull(spawn.getBoneAnimationId());assertEquals(1,owner.getClips().size());
    }
    private ActorBoneAnimationDraft capture() throws Exception {
        var method=controller.getClass().getDeclaredMethod("captureAnimationDraft");method.setAccessible(true);
        return (ActorBoneAnimationDraft)method.invoke(controller);
    }
    @Test void firstDetachedGesturePreviewsWithoutPublishingAndCommitPreservesInput() throws Exception {
        spawn.setBlendSettings(new com.director.actor.rig.skeleton.PoseBlendSettings(
                com.director.actor.rig.skeleton.PoseLayer.BlendMode.ADDITIVE,.4f,2,true,java.util.Set.of(),4,1));
        set("gestureTime",5f);begin();assertNull(capture());
        ((BoneTrack)get("editingBoneTrack")).rotation().put(2,new Quaternionf().rotateY(.7f),InterpolationMode.LINEAR);
        var preview=capture();assertNotNull(preview);assertEquals(.5f,preview.envelope(),1e-6f);
        assertNull(spawn.getBoneAnimationId());assertTrue(asset.boneTracks().isEmpty());
        assertFalse(AnimationAssetLibrary.getInstance().contains(asset.id()));assertEquals(0,history.getUndoSize());
        invoke("registerAnimationUndo",new Class[]{TimelineModel.class},model);
        assertTrue(preview.authoredPose().localTransform("head").toMatrix().equals(
                asset.sample(2,AnimationLoopMode.ONCE).localTransform("head").toMatrix(),1e-6f));
        assertEquals(preview.settings(),spawn.getBoneAnimation().getBlendSettings());assertEquals(1,owner.getClips().size());
        assertTrue(history.undo());assertNull(spawn.getBoneAnimationId());assertEquals(1,owner.getClips().size());
    }
    @Test void translationAndScaleDraftCaptureDoesNotWriteAssetOrCreateBinding() throws Exception {
        for(var mode:java.util.List.of(com.director.client.gizmo.GizmoMode.TRANSLATE,com.director.client.gizmo.GizmoMode.SCALE)) {
            set("activeBoneMode",mode);set("gestureTime",5f);begin();
            var track=(BoneTrack)get("editingBoneTrack");
            if(mode==com.director.client.gizmo.GizmoMode.TRANSLATE) track.translation().put(2,new org.joml.Vector3f(1,2,3),InterpolationMode.LINEAR);
            else track.scale().put(2,new org.joml.Vector3f(2),InterpolationMode.LINEAR);
            var input=capture();assertNotNull(input);assertTrue(asset.boneTracks().isEmpty());assertNull(spawn.getBoneAnimationId());
            if(mode==com.director.client.gizmo.GizmoMode.TRANSLATE) assertEquals(new org.joml.Vector3f(1,2,3),input.authoredPose().localTransform("head").translation());
            else assertEquals(new org.joml.Vector3f(2),input.authoredPose().localTransform("head").scale());
            invoke("clearDraft",new Class[]{});assertNull(capture());
        }
        assertEquals(0,history.getUndoSize());assertEquals(1,owner.getClips().size());
    }
}

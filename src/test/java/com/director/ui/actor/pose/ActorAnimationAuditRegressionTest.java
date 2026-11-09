package com.director.ui.actor.pose;

import com.director.actor.Actor;
import com.director.actor.ActorType;
import com.director.actor.ActorRegistry;
import com.director.actor.ActorRegistryManager;
import com.director.actor.ActorRef;
import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.timeline.playback.entity.ActorAnimationLayerSample;
import com.director.ui.actor.track.*;
import com.director.ui.timeline.commands.*;
import org.junit.jupiter.api.*;
import org.joml.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorAnimationAuditRegressionTest {
    TimelineModel model; TimelineTrack owner; Actor actor; EntitySpawnClip spawn;
    List<String> ids=new ArrayList<>();
    @BeforeEach void setup() {
        ActorRegistryManager.setRegistry(null);ActorRegistryManager.setRegistry(new ActorRegistry());
        actor=new Actor("Audit",ActorType.PLAYER);ActorRegistryManager.getInstance().registerActor(actor);
        model=new TimelineModel(new TimelineViewportState());model.setCurrentTime(1);
        owner=new TimelineTrack("Audit",TrackType.ACTOR);var payload=new ActorTrack();payload.setActorRef(new ActorRef(actor.getActorId()));
        owner.setActorTrackPayload(payload);model.addTrack(owner);
        spawn=new EntitySpawnClip("Audit",0,5);payload.addClip(spawn);owner.addClip(new EntityTimelineClip(spawn,0));
        model.selectItem(owner,false);TimelineCommandBridge.bind(new TimelineCommandHistory());
    }
    @AfterEach void cleanup() {
        if(spawn.getBoneAnimationId()!=null) ids.add(spawn.getBoneAnimationId());
        ids.forEach(AnimationAssetLibrary.getInstance()::remove);TimelineCommandBridge.bind(null);ActorRegistryManager.setRegistry(null);
    }
    AnimationAsset asset() {
        var a=new AnimationAsset("audit:"+UUID.randomUUID(),"Audit",ArticulatedPlayerRig.DEFINITION,5,AnimationLoopMode.ONCE);
        AnimationAssetLibrary.getInstance().register(a);ids.add(a.id());spawn.setBoneAnimationId(a.id());return a;
    }
    @Test void timelineDetailShouldRecognizeSpawnOwnedBoneAnimation() {
        var a=asset();a.getOrCreateBoneTrack(HumanoidRig.HEAD).rotation().put(1,new Quaternionf().rotateY(.4f),InterpolationMode.LINEAR);
        assertNotNull(ActorAnimationEditContext.resolve(model,actor.getActorId()));
        assertEquals(ActorAnimationDetailTarget.Status.READY,ActorAnimationDetailTarget.resolve(model).status());
    }
    @Test void fullPoseMirrorShouldIncludeElbowJoints() {
        var a=asset();a.getOrCreateBoneTrack(ArticulatedPlayerRig.RIGHT_FOREARM).rotation().put(1,new Quaternionf().rotateX(.8f),InterpolationMode.LINEAR);
        var context=ActorAnimationEditContext.resolve(model,actor.getActorId());
        assertTrue(ActorAnimationAuthoringService.mirror(context,List.of(1f),InterpolationMode.LINEAR));
        assertTrue(a.findBoneTrack(ArticulatedPlayerRig.LEFT_FOREARM).flatMap(t->t.rotation().findAt(1)).isPresent(),"Mirroring must include opposite elbow");
    }
    @Test void newTransformShouldPreserveCurrentScaleAndRoll() {
        spawn.setScale(2,3,4);spawn.setRollDeg(35);
        var wrapper=ActorTrackClipAuthoringService.getInstance().createClip(model,owner,ActorTrackClipKind.TRANSFORM,null);
        var c=(EntityTransformClip)wrapper.getEntityClip();
        assertEquals(new Vector3f(2,3,4),c.getFromScale());
        assertEquals(35,c.getFromRot().z);
    }
    @Test void fullPoseMirrorReflectsTranslationAndSwapsScale() {
        var asset = asset();
        asset.getOrCreateBoneTrack(HumanoidRig.ROOT).translation().put(1, new Vector3f(2, 3, 4), InterpolationMode.STEP);
        asset.getOrCreateBoneTrack(HumanoidRig.LEFT_ARM).scale().put(1, new Vector3f(2, 3, 4), InterpolationMode.LINEAR);
        var context = ActorAnimationEditContext.resolve(model, actor.getActorId());
        assertTrue(ActorAnimationAuthoringService.mirror(context, List.of(1f), InterpolationMode.LINEAR));
        var root = asset.findBoneTrack(HumanoidRig.ROOT).orElseThrow().translation().findAt(1).orElseThrow();
        assertEquals(new Vector3f(-2, 3, 4), root.value());
        assertEquals(InterpolationMode.STEP, root.interpolation());
        assertEquals(new Vector3f(2, 3, 4), asset.findBoneTrack(HumanoidRig.RIGHT_ARM).orElseThrow()
                .scale().findAt(1).orElseThrow().value());
        assertEquals(new Vector3f(1), asset.findBoneTrack(HumanoidRig.LEFT_ARM).orElseThrow()
                .scale().findAt(1).orElseThrow().value());
    }
    @Test void zeroAnimationSpeedIsNormalizedToTheVisibleMinimum() {
        var clip=new EntityAnimationClip("Animation",0,5);clip.setSpeed(0);
        assertEquals(.01f,clip.getSpeed());
        clip.setSpeed(Float.NaN);assertEquals(1,clip.getSpeed());
    }
}


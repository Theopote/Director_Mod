package com.director.actor.runtime;

import com.director.actor.*;
import com.director.timeline.actor.ActorTimelineSource;
import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActorHistoricalTargetTest {
    private final ActorRuntimeController controller=ActorRuntimeController.getInstance();
    private final ActorRegistry registry=new ActorRegistry();
    private final List<TimelineTrack> tracks=new ArrayList<>();
    private final Map<String,Object> original=new HashMap<>();
    @BeforeEach void bindFixture() throws Exception {
        var source=new ActorTimelineSource() {
            public float getCurrentTime(){return 99;}
            public List<TimelineTrack> getTracks(){return tracks;}
            public void addTrackStructureListener(TrackStructureListener l){}
            public void removeTrackStructureListener(TrackStructureListener l){}
        };
        for(var entry:Map.of("timelineSource",source,"actorRegistry",registry).entrySet()) {
            var field=ActorRuntimeController.class.getDeclaredField(entry.getKey());field.setAccessible(true);
            original.put(entry.getKey(),field.get(controller));field.set(controller,entry.getValue());
        }
    }
    @AfterEach void restore() throws Exception {
        for(var entry:original.entrySet()) {
            var field=ActorRuntimeController.class.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(controller,entry.getValue());
        }
    }
    private UUID actor() {
        var actor=new com.director.actor.Actor("Actor",ActorType.PLAYER);registry.registerActor(actor);
        var track=new TimelineTrack("Actor",TrackType.ACTOR);var payload=track.getOrCreateActorTrackPayload();
        payload.setActorRef(new ActorRef(actor.getActorId()));payload.addClip(new EntitySpawnClip("Spawn",0,10));
        var route=new ActorRouteClip("Route",0,10);route.setWaypoints(List.of(new Vector3f(),new Vector3f(10,0,0)));payload.addClip(route);
        tracks.add(track);return actor.getActorId();
    }
    @Test void actorTargetUsesRequestedTimeWithoutPreviewOrCurrentPlayhead() {
        var id=actor();var a=controller.sampleHistoricalActorEye(id,2);assertNotNull(a);
        var b=controller.sampleHistoricalActorEye(id,8);assertNotNull(b);assertTrue(b.x-a.x>4);
        assertEquals(a,controller.sampleHistoricalActorEye(id,2));
        assertNull(controller.sampleHistoricalActorEye(id,12));assertNull(controller.sampleHistoricalActorEye(id,Float.NaN));
        tracks.getFirst().setMuted(true);assertNull(controller.sampleHistoricalActorEye(id,2));
    }
    @Test void mutualGazeDoesNotRecurseOrDependOnQueryOrder() {
        var a=actor();var b=actor();
        for(int i=0;i<2;i++) {
            var gaze=new EntityLookAtClip("Gaze",0,10);gaze.setMode(EntityLookAtClip.TargetMode.ENTITY_UUID);
            gaze.setTargetEntityUuid(i==0?b:a);tracks.get(i).getActorTrackPayload().addClip(gaze);
        }
        var first=controller.sampleHistoricalActorEye(a,3);assertNotNull(first);
        assertNotNull(controller.sampleHistoricalActorEye(b,3));
        assertEquals(first,controller.sampleHistoricalActorEye(a,3));
        assertNull(controller.sampleHistoricalActorEye(UUID.randomUUID(),3));
    }
    @Test void historicalHeadSocketEntityGazeMatchesSameTimeWorldPoint() throws Exception {
        actor();var target=actor();
        var targetRoute=tracks.get(1).getActorTrackPayload().getClips().stream().filter(ActorRouteClip.class::isInstance).map(ActorRouteClip.class::cast).findFirst().orElseThrow();
        targetRoute.setWaypoints(List.of(new Vector3f(0,0,4),new Vector3f(0,0,14)));
        var gaze=new EntityLookAtClip("Gaze",0,10);gaze.setMode(EntityLookAtClip.TargetMode.ENTITY_UUID);gaze.setTargetEntityUuid(target);
        tracks.getFirst().getActorTrackPayload().addClip(gaze);
        var type=Class.forName("com.director.actor.runtime.ActorRuntimeController$TrackRuntime");
        var ctor=type.getDeclaredConstructor(ActorRuntimeController.class,TimelineTrack.class);ctor.setAccessible(true);
        var runtime=ctor.newInstance(controller,tracks.getFirst());
        var method=type.getDeclaredMethod("samplePropLaunchTransform",net.minecraft.entity.Entity.class,float.class,
                com.director.actor.prop.ActorPropSlot.class,com.director.actor.prop.ActorPropTimelineSampler.Attachment.class);
        method.setAccessible(true);
        var attachment=new com.director.actor.prop.ActorPropTimelineSampler.Attachment("stone",0,0,0,0,0,0);
        var slot=com.director.actor.prop.ActorPropSlot.HEAD;
        var dynamic=(org.joml.Matrix4f)method.invoke(runtime,null,3f,slot,attachment);assertNotNull(dynamic);
        method.invoke(runtime,null,7f,slot,attachment);
        var repeat=(org.joml.Matrix4f)method.invoke(runtime,null,3f,slot,attachment);assertTrue(dynamic.equals(repeat,1e-6f));
        var eye=controller.sampleHistoricalActorEye(target,3);
        gaze.setMode(EntityLookAtClip.TargetMode.WORLD_POINT);
        assertNotNull(eye);
        gaze.setWorldPoint((float)eye.x,(float)eye.y,(float)eye.z);
        var fixed=(org.joml.Matrix4f)method.invoke(runtime,null,3f,slot,attachment);
        assertTrue(dynamic.equals(fixed,1e-6f));
    }
    @Test void pathRootOffsetIsScaledRotatedAndConsumedOnceInHistoricalQueries() {
        var id=actor();var payload=tracks.getFirst().getActorTrackPayload();
        var spawn=payload.getClips().stream().filter(EntitySpawnClip.class::isInstance).map(EntitySpawnClip.class::cast).findFirst().orElseThrow();
        spawn.setScale(2,3,4);
        var before=controller.sampleHistoricalActorEye(id,3);assertNotNull(before);
        var library=com.director.actor.animation.asset.AnimationAssetLibrary.getInstance();
        String animationId="test:historical-root";
        var asset=new com.director.actor.animation.asset.AnimationAsset(animationId,"Root",
                com.director.actor.rig.skeleton.ArticulatedPlayerRig.DEFINITION,10,com.director.actor.animation.asset.AnimationLoopMode.ONCE);
        asset.getOrCreateBoneTrack("root").translation().put(0,new Vector3f(.5f,1,2),com.director.actor.animation.asset.InterpolationMode.LINEAR);
        library.register(asset);
        try {
            spawn.setBoneAnimationId(animationId);
            var after=controller.sampleHistoricalActorEye(id,3);assertNotNull(after);
            assertTrue(after.subtract(before).distanceTo(new net.minecraft.util.math.Vec3d(8,3,-1))<1e-4);
            controller.sampleHistoricalActorEye(id,7);
            assertEquals(after,controller.sampleHistoricalActorEye(id,3));
            assertEquals(new Vector3f(.5f,1,2),asset.sample(3).localTransform("root").translation());
            spawn.setBoneAnimationId(null);
            assertEquals(before,controller.sampleHistoricalActorEye(id,3));
        } finally {library.remove(animationId);}
    }
}

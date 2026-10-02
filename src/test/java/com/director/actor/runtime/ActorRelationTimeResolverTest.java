package com.director.actor.runtime;
import com.director.timeline.playback.entity.EntitySample;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.BiFunction;
import static org.junit.jupiter.api.Assertions.*;
class ActorRelationTimeResolverTest {
    UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
    EntitySample base(float x,float yaw) { var s=new EntitySample(); s.position.set(x,0,0); s.rotation.x=yaw; return s; }
    EntitySample follower(UUID target) { var s=base(10,0); s.relationEnabled=true; s.relationType="FOLLOW"; s.relationTargetActorId=target; s.relationDistance=2; s.relationMaintainLookAt=false; return s; }
    @Test void targetIsSampledAtExplicitTimeAndRewindRecomputes() {
        for(float time:new float[]{5,1,5}) { var s=follower(b); ActorRelationTimeResolver.resolveInto(a,s,time,(id,t)->base(t,0)); assertEquals(new Vector3f(time,0,2),s.position); }
    }
    @Test void nestedRelationsResolveWithoutPreviewOrder() {
        var s=follower(b); ActorRelationTimeResolver.resolveInto(a,s,3,(id,t)->id.equals(b)?follower(c):base(t,0));
        assertEquals(new Vector3f(3,0,4),s.position);
    }
    @Test void targetYawAndOffsetsAreApplied() {
        var s=follower(b); s.relationAngle=90; s.relationHeightOffset=3;
        ActorRelationTimeResolver.resolveInto(a,s,1,(id,t)->base(4,0)); assertTrue(s.position.distance(new Vector3f(2,3,0))<.0001f);
    }
    @Test void cyclesFallbackWithoutPartialSourceWrites() {
        var s=follower(b); ActorRelationTimeResolver.resolveInto(a,s,1,(id,t)->id.equals(b)?follower(a):follower(b));
        assertEquals(new Vector3f(10,0,0),s.position);
        s=follower(a); ActorRelationTimeResolver.resolveInto(a,s,1,(id,t)->follower(a)); assertEquals(new Vector3f(10,0,0),s.position);
    }
    @Test void missingAndInactiveTargetsPreserveTrackPosition() {
        var s=follower(b); ActorRelationTimeResolver.resolveInto(a,s,1,(id,t)->null); assertEquals(10,s.position.x);
        var target=base(4,0); target.active=false; ActorRelationTimeResolver.resolveInto(a,s,1,(id,t)->target); assertEquals(10,s.position.x);
    }
    @Test void unsupportedRelationDoesNotRewritePosition() {
        var s=follower(b); s.relationType="PASS_OBJECT"; ActorRelationTimeResolver.resolveInto(a,s,1,(id,t)->base(4,0)); assertEquals(10,s.position.x);
    }
    @Test void maintainGazeWritesWorldPointAndLaunchYawMatchesPreviewFormula() {
        var s=follower(b); s.relationMaintainLookAt=true; ActorRelationTimeResolver.resolveInto(a,s,1,(id,t)->base(0,0));
        assertEquals("WORLD_POINT",s.lookAtMode); assertEquals(new Vector3f(0,1.6f,0),s.lookAtPoint);
        float yaw=ActorRelationTimeResolver.worldLookYaw(s); ActorRelationTimeResolver.applyWorldLookRotation(s); assertEquals(yaw,s.rotation.x);
    }
    @Test void relativePositionDoesNotForceLookAt() {
        var s=follower(b); s.relationMaintainLookAt=true; s.relationType="RELATIVE_POSITION";
        ActorRelationTimeResolver.resolveInto(a,s,1,(id,t)->base(0,0)); assertFalse(s.lookAtEnabled);
    }
    @Test void nonFiniteRelationValuesCannotPoisonPosition() {
        var s=follower(b); s.relationDistance=Float.NaN; ActorRelationTimeResolver.resolveInto(a,s,1,(id,t)->base(0,0)); assertTrue(s.position.isFinite()); assertEquals(10,s.position.x);
    }
    @Test void placementConstraintIsAppliedInsideTargetChain() {
        var s=follower(b);
        ActorRelationTimeResolver.resolveInto(a,s,3,(id,t)->id.equals(b)?follower(c):base(3,0),
                (id,t,value,requested)->new Vector3f(requested.x,1,requested.z));
        assertEquals(new Vector3f(3,1,4),s.position);
    }
    @Test void unsafePlacementHidesSourceAndPreventsFloatingPreview() {
        var s=follower(b); ActorRelationTimeResolver.resolveInto(a,s,1,(id,t)->base(0,0),(id,t,value,requested)->null);
        assertEquals(0,s.visibility); assertEquals(0,s.speed); assertEquals(10,s.position.x);
    }
    @Test void legacyCycleReportsReasonWithoutChangingSourcePosition() {
        var messages=new ArrayList<String>(); var s=follower(a);
        ActorRelationTimeResolver.resolveInto(a,s,1,(id,t)->follower(a),(id,t,value,requested)->requested,
                (id,message)->messages.add(message));
        assertEquals(1,messages.size()); assertEquals(10,s.position.x);
    }
}

package com.director.actor.runtime;

import com.director.actor.ActorBehaviorCapabilities;
import com.director.timeline.model.entity.ActorRelationClip;
import com.director.timeline.playback.entity.EntitySample;
import java.util.*;
import java.util.function.BiFunction;

/** Resolves relation placement against samples at one explicit time, never preview entities. */
public final class ActorRelationTimeResolver {
    private ActorRelationTimeResolver() {}
    @FunctionalInterface public interface PlacementConstraint {
        org.joml.Vector3f project(UUID actor,float time,EntitySample sample,org.joml.Vector3f requested);
    }
    public static void resolveInto(UUID actor, EntitySample sample, float time, BiFunction<UUID,Float,EntitySample> source) {
        resolveInto(actor,sample,time,source,(id,at,value,requested)->requested);
    }
    public static void resolveInto(UUID actor, EntitySample sample, float time, BiFunction<UUID,Float,EntitySample> source,PlacementConstraint constraint) {
        if (actor==null || sample==null || !Float.isFinite(time)) return;
        try { resolve(actor,sample,time,source,constraint,new HashSet<>()); }
        catch (Cycle ignored) { /* A legacy cyclic chain falls back to the actor's own track placement. */ }
    }
    private static void resolve(UUID actor,EntitySample sample,float time,BiFunction<UUID,Float,EntitySample> source,PlacementConstraint constraint,Set<UUID> visiting) {
        if (!visiting.add(actor) || visiting.size()>128) throw new Cycle();
        try {
            if (!sample.relationEnabled || sample.relationTargetActorId==null || sample.relationType==null) return;
            ActorRelationClip.RelationType type;
            try { type=ActorRelationClip.RelationType.valueOf(sample.relationType); }
            catch(IllegalArgumentException invalid) { return; }
            if (!ActorBehaviorCapabilities.supportsRelation(type) || !Float.isFinite(sample.relationDistance) || sample.relationDistance < .1f
                    || !Float.isFinite(sample.relationAngle) || !Float.isFinite(sample.relationHeightOffset)) return;
            var target=source.apply(sample.relationTargetActorId,time);
            if (target==null || !target.active || target.visibility<=.01f || !target.position.isFinite() || !target.rotation.isFinite()) return;
            resolve(sample.relationTargetActorId,target,time,source,constraint,visiting);
            if (target.visibility<=.01f) { sample.visibility=0; sample.speed=0; return; }
            float yaw=worldLookYaw(target);
            double radians=Math.toRadians(yaw+sample.relationAngle);
            var position=new org.joml.Vector3f(target.position.x-(float)Math.sin(radians)*sample.relationDistance,
                    target.position.y+sample.relationHeightOffset,target.position.z+(float)Math.cos(radians)*sample.relationDistance);
            if (!position.isFinite()) return;
            position=constraint.project(actor,time,sample,position);
            if (position==null || !position.isFinite()) { sample.visibility=0; sample.speed=0; return; }
            sample.position.set(position);
            if (sample.relationMaintainLookAt && type!=ActorRelationClip.RelationType.RELATIVE_POSITION) {
                sample.lookAtEnabled=true; sample.lookAtMode="WORLD_POINT";
                sample.lookAtPoint.set(target.position.x,target.position.y+1.6f,target.position.z);
                sample.lookAtStrength=Math.max(.85f,sample.lookAtStrength);
            }
        } finally { visiting.remove(actor); }
    }
    private static boolean worldLook(EntitySample s) {
        return s.lookAtEnabled && (s.lookAtMode==null || "WORLD_POINT".equals(s.lookAtMode))
                && s.lookAtPoint.isFinite() && Float.isFinite(s.lookAtStrength)
                && s.lookAtPoint.distance(s.position)>=.01f;
    }
    public static float worldLookYaw(EntitySample s) {
        if (!worldLook(s)) return s.rotation.x;
        var d=new org.joml.Vector3f(s.lookAtPoint).sub(s.position);
        float target=(float)Math.toDegrees(Math.atan2(d.z,d.x))-90;
        return s.rotation.x+(target-s.rotation.x)*Math.max(0,Math.min(1,s.lookAtStrength));
    }
    /** Match the preview's world-point gaze when sampling a launch matrix, without mutating an entity. */
    public static void applyWorldLookRotation(EntitySample s) {
        if (!worldLook(s)) return;
        var d=new org.joml.Vector3f(s.lookAtPoint).sub(s.position).normalize();
        float strength=Math.max(0,Math.min(1,s.lookAtStrength));
        s.rotation.x=worldLookYaw(s);
        float pitch=(float)-Math.toDegrees(Math.asin(d.y));
        s.rotation.y+=(pitch-s.rotation.y)*strength;
    }
    private static final class Cycle extends RuntimeException {}
}

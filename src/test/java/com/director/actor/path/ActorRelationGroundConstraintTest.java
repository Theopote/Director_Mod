package com.director.actor.path;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.OptionalDouble;
import static org.junit.jupiter.api.Assertions.*;
class ActorRelationGroundConstraintTest {
    GroundRoute.Terrain terrain(double wall,double edge) { return new GroundRoute.Terrain() {
        public OptionalDouble floor(double x,double z,double y,double up,double down) { return x>edge?OptionalDouble.empty():OptionalDouble.of(0); }
        public boolean clear(Vec3d p) { return p.x<wall; }
    }; }
    @Test void heightOffsetCannotKeepGroundActorInAir() {
        var result=ActorRelationGroundConstraint.project(new Vec3d(0,0,0),new Vec3d(3,100,0),terrain(100,100));
        assertEquals(new Vec3d(3,0,0),result.position());
    }
    @Test void wallStopsAtReachablePoint() {
        var result=ActorRelationGroundConstraint.project(Vec3d.ZERO,new Vec3d(3,0,0),terrain(1,100));
        assertTrue(result.position().x<1); assertFalse(result.warning().isEmpty());
    }
    @Test void missingFloorCannotBeCrossed() {
        var result=ActorRelationGroundConstraint.project(Vec3d.ZERO,new Vec3d(3,0,0),terrain(100,1));
        assertTrue(result.position().x<=1); assertEquals(0,result.position().y); assertFalse(result.warning().isEmpty());
    }
    @Test void unsafeStartHasNoFallbackFloatingPosition() {
        assertNull(ActorRelationGroundConstraint.project(Vec3d.ZERO,new Vec3d(3,0,0),terrain(100,-1)).position());
    }
    @Test void distantRequestsHaveBoundedDetection() {
        var result=ActorRelationGroundConstraint.project(Vec3d.ZERO,new Vec3d(10000,0,0),terrain(100000,100000));
        assertEquals(64,result.position().x); assertFalse(result.warning().isEmpty());
    }
    @Test void sameHorizontalPositionProjectsOntoFloor() {
        var result=ActorRelationGroundConstraint.project(new Vec3d(0,1,0),new Vec3d(0,10,0),terrain(100,100)); assertEquals(Vec3d.ZERO,result.position());
    }
    @Test void nonFinitePositionIsRejected() {
        assertNull(ActorRelationGroundConstraint.project(Vec3d.ZERO,new Vec3d(Double.NaN,0,0),terrain(100,100)).position());
    }
}

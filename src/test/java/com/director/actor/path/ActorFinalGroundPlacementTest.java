package com.director.actor.path;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.OptionalDouble;
import static org.junit.jupiter.api.Assertions.*;
class ActorFinalGroundPlacementTest {
    GroundRoute.Terrain flat=new GroundRoute.Terrain() {
        public OptionalDouble floor(double x,double z,double y,double up,double down) { return OptionalDouble.of(0); }
        public boolean clear(Vec3d feet) { return true; }
    };
    @Test void horizontalRootMotionKeepsActorOnGround() {
        var value=ActorFinalGroundPlacement.project(Vec3d.ZERO,new Vec3d(2,0,1),0,flat); assertEquals(new Vec3d(2,0,1),value.position());
    }
    @Test void verticalRootMotionCannotCreatePersistentFlight() {
        var value=ActorFinalGroundPlacement.project(Vec3d.ZERO,new Vec3d(0,20,0),0,flat); assertEquals(Vec3d.ZERO,value.position()); assertFalse(value.warning().isEmpty());
    }
    @Test void jumpIsAppliedOnceEvenWhenBaseAlreadyContainsHop() {
        var value=ActorFinalGroundPlacement.project(new Vec3d(0,1,0),new Vec3d(2,0,0),1,flat); assertEquals(new Vec3d(2,1,0),value.position());
    }
    @Test void ascentCannotSkipThroughObstacleWithClearEndpoint() {
        var terrain=new GroundRoute.Terrain() {
            public OptionalDouble floor(double x,double z,double y,double up,double down) { return OptionalDouble.of(0); }
            public boolean clear(Vec3d feet) { return feet.y<.4 || feet.y>.6; }
        };
        var value=ActorFinalGroundPlacement.project(Vec3d.ZERO,new Vec3d(1,0,0),1,terrain); assertEquals(new Vec3d(1,0,0),value.position()); assertFalse(value.warning().isEmpty());
    }
    @Test void rootCannotCrossWall() {
        var terrain=new GroundRoute.Terrain() {
            public OptionalDouble floor(double x,double z,double y,double up,double down) { return OptionalDouble.of(0); }
            public boolean clear(Vec3d feet) { return feet.x<1; }
        };
        var value=ActorFinalGroundPlacement.project(Vec3d.ZERO,new Vec3d(3,0,0),0,terrain); assertTrue(value.position().x<1);
    }
    @Test void replayDoesNotAccumulateRootOffsets() {
        var a=ActorFinalGroundPlacement.project(Vec3d.ZERO,new Vec3d(2,0,0),.5,flat);
        var b=ActorFinalGroundPlacement.project(Vec3d.ZERO,new Vec3d(2,0,0),.5,flat); assertEquals(a,b);
    }
    @Test void missingTerrainAndNonFiniteRootFailSafely() {
        assertNull(ActorFinalGroundPlacement.project(Vec3d.ZERO,Vec3d.ZERO,0,null).position());
        assertNull(ActorFinalGroundPlacement.project(Vec3d.ZERO,new Vec3d(0,Double.NaN,0),0,flat).position());
    }
    @Test void jumpHeightIsBoundedAndEndsAccordingToTimeCurve() {
        assertEquals(1.25,ActorFinalGroundPlacement.project(Vec3d.ZERO,Vec3d.ZERO,100,flat).position().y);
        assertEquals(0,GroundRoute.jumpOffset(5,0,.8,1.25));
    }
}

package com.director.actor.path;
import net.minecraft.util.math.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ActorGroundTerrainTest {
    ActorGroundTerrain terrain(ActorCollisionDimensions dimensions,List<Box> blocks) {
        return new ActorGroundTerrain(new ActorGroundTerrain.CollisionWorld() {
            public boolean loaded(Box bounds) { return true; }
            public Iterable<Box> collisions(Box bounds) { return blocks.stream().filter(bounds::intersects).toList(); }
        },dimensions);
    }
    @Test void floorSamplingWorksWithoutAnyPreviewEntity() {
        var terrain=terrain(new ActorCollisionDimensions(.6,1.8),List.of(new Box(-10,-1,-10,10,0,10)));
        assertEquals(0,terrain.floor(0,0,0,.6,1.25).orElseThrow()); assertTrue(terrain.clear(Vec3d.ZERO));
    }
    @Test void slabTopAndLowCeilingAreDetected() {
        var terrain=terrain(new ActorCollisionDimensions(.6,1.8),List.of(new Box(-1,0,-1,1,.5,1)));
        assertEquals(.5,terrain.floor(0,0,0,.6,1.25).orElseThrow());
        terrain=terrain(new ActorCollisionDimensions(.6,1.8),List.of(new Box(-1,-1,-1,1,0,1),new Box(-1,1,-1,1,1.2,1)));
        assertFalse(terrain.clear(Vec3d.ZERO)); assertTrue(terrain.floor(0,0,0,.6,1.25).isEmpty());
    }
    @Test void sampleScaleChangesCollisionEnvelope() {
        var blocks=List.of(new Box(.5,0,-1,.6,2,1)); var dimensions=new ActorCollisionDimensions(.6,1.8);
        assertTrue(terrain(dimensions,blocks).clear(Vec3d.ZERO));
        assertFalse(terrain(dimensions.scaled(new Vector3f(2,1,1)),blocks).clear(Vec3d.ZERO));
    }
    @Test void tinyScaledActorHasNonInvertedBodyBox() {
        var dimensions=new ActorCollisionDimensions(.5,1.8).scaled(new Vector3f(.0001f));
        var terrain=terrain(dimensions,List.of()); assertTrue(terrain.clear(Vec3d.ZERO));
    }
    @Test void unloadedBoundsAreNeverQueriedForCollisions() {
        var terrain=new ActorGroundTerrain(new ActorGroundTerrain.CollisionWorld() {
            public boolean loaded(Box bounds) { return false; }
            public Iterable<Box> collisions(Box bounds) { fail("Unloaded collision query"); return List.of(); }
        },new ActorCollisionDimensions(.6,1.8));
        assertFalse(terrain.clear(Vec3d.ZERO)); assertTrue(terrain.floor(0,0,0,.6,1.25).isEmpty());
    }
    @Test void invalidDimensionsAndScaleRejectBeforeSampling() {
        assertThrows(IllegalArgumentException.class,()->new ActorCollisionDimensions(0,1));
        assertThrows(IllegalArgumentException.class,()->new ActorCollisionDimensions(1,Double.NaN));
        assertThrows(IllegalArgumentException.class,()->new ActorCollisionDimensions(1,1).scaled(new Vector3f(-1,1,1)));
    }
    @Test void nonFiniteCoordinatesAreRejected() {
        var terrain=terrain(new ActorCollisionDimensions(.6,1.8),List.of());
        assertTrue(terrain.floor(Double.NaN,0,0,.6,1).isEmpty()); assertFalse(terrain.clear(new Vec3d(0,Double.POSITIVE_INFINITY,0)));
    }
    @Test void identicalWorldAndDimensionsGiveIdenticalHistoricalResult() {
        var dimensions=new ActorCollisionDimensions(.6,1.8); var blocks=List.of(new Box(-10,-1,-10,10,0,10));
        var a=terrain(dimensions,blocks); var b=terrain(dimensions,blocks);
        assertEquals(ActorRelationGroundConstraint.project(Vec3d.ZERO,new Vec3d(3,0,0),a),ActorRelationGroundConstraint.project(Vec3d.ZERO,new Vec3d(3,0,0),b));
    }
}

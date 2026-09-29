package com.director.actor.path;

import com.director.timeline.model.entity.ActorRouteClip;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.DoubleUnaryOperator;
import static org.junit.jupiter.api.Assertions.*;

class GroundRouteTest {
    private GroundRoute.Terrain terrain(DoubleUnaryOperator heights) {
        return new GroundRoute.Terrain() {
            public OptionalDouble floor(double x, double z, double y, double up, double down) {
                double floor = heights.applyAsDouble(x);
                return Double.isFinite(floor) && floor <= y + up + 1e-6 && floor >= y - down - 1e-6
                        ? OptionalDouble.of(floor) : OptionalDouble.empty();
            }
            public boolean clear(Vec3d feet) { return true; }
        };
    }
    private List<Vec3d> line() { return List.of(new Vec3d(0, 0, 0), new Vec3d(4, 0, 0)); }

    @Test void raisedWaypointsDoNotMakeGroundActorFly() {
        var route = GroundRoute.compile(List.of(new Vec3d(0, 20, 0), new Vec3d(4, 40, 0)), terrain(x -> 0));
        assertTrue(route.valid());
        assertEquals(4, route.length(), 1e-5);
        assertEquals(0, route.sample(1, 2, ActorRouteClip.LoopMode.ONCE).position().y, 1e-5);
    }

    @Test void smallStepAllowedButCliffAndGapStopAtLastSafePoint() {
        var step = GroundRoute.compile(line(), terrain(x -> x < 2 ? 0 : 0.5));
        assertEquals(4, step.length(), 1e-5);
        assertEquals(0.5, step.sample(10, 2, ActorRouteClip.LoopMode.ONCE).position().y, 1e-5);
        for (double inaccessible : new double[]{2, -4, Double.NaN}) {
            var route = GroundRoute.compile(line(), terrain(x -> x < 2 ? 0 : inaccessible));
            assertTrue(route.valid());
            assertFalse(route.warning().isEmpty());
            var stopped = route.sample(10, 2, ActorRouteClip.LoopMode.LOOP);
            assertTrue(stopped.position().x < 2);
            assertFalse(stopped.moving());
            assertEquals(0, stopped.position().y, 1e-5);
        }
    }

    @Test void unsupportedStartIsInvalidRatherThanFloating() {
        assertFalse(GroundRoute.compile(line(), terrain(x -> Double.NaN)).valid());
        assertFalse(GroundRoute.compile(List.of(), terrain(x -> 0)).valid());
    }

    @Test void bodyObstructionAndRepeatedAnchorsAreHandled() {
        var terrain = new GroundRoute.Terrain() {
            public OptionalDouble floor(double x, double z, double y, double up, double down) { return OptionalDouble.of(0); }
            public boolean clear(Vec3d feet) { return feet.x < 1; }
        };
        var blocked = GroundRoute.compile(line(), terrain);
        assertFalse(blocked.warning().isEmpty());
        assertTrue(blocked.sample(100, 2, ActorRouteClip.LoopMode.ONCE).position().x < 1);
        var stationary = GroundRoute.compile(List.of(Vec3d.ZERO, Vec3d.ZERO), terrain);
        assertTrue(stationary.valid());
        assertFalse(stationary.sample(100, 2, ActorRouteClip.LoopMode.LOOP).moving());
        assertFalse(GroundRoute.compile(List.of(new Vec3d(Double.NaN, 0, 0)), terrain).valid());
    }

    @Test void replayAndPingPongHaveDeterministicPositionsAndDirections() {
        var route = GroundRoute.compile(line(), terrain(x -> 0));
        var direct = route.sample(3, 2, ActorRouteClip.LoopMode.PING_PONG);
        route.sample(1, 2, ActorRouteClip.LoopMode.PING_PONG);
        assertEquals(direct, route.sample(3, 2, ActorRouteClip.LoopMode.PING_PONG));
        assertEquals(2, direct.position().x, 1e-5);
        assertTrue(direct.direction().x < 0);
    }

    @Test void hopHeightAndAirTimeAreBoundedAndDoNotRepeat() {
        assertEquals(0, GroundRoute.jumpOffset(1, 2, 10, 100));
        assertEquals(1.25, GroundRoute.jumpOffset(2.4, 2, 10, 100), 1e-5);
        assertEquals(0, GroundRoute.jumpOffset(2.8, 2, 10, 100), 1e-5);
        assertEquals(0, GroundRoute.jumpOffset(20, 2, 10, 100));
    }

    @Test void routeSettingsRejectNonfiniteValuesAndInvalidateTerrain() {
        var clip = new ActorRouteClip("route", 0, 10);
        clip.addWaypoint(0, 0, 0);
        clip.setWaypoints(clip.getWaypoints());
        assertEquals(1, clip.getWaypoints().size());
        long revision = clip.getTerrainRevision();
        clip.setWaypoint(0, 1, 0, 0);
        assertTrue(clip.getTerrainRevision() > revision);
        clip.addWaypoint(Float.NaN, 0, 0);
        assertEquals(1, clip.getWaypoints().size());
        clip.setJumpHeight(100);
        clip.setJumpDuration(100);
        clip.setSpeed(Float.NaN);
        assertEquals(1.25f, clip.getJumpHeight());
        assertEquals(0.8f, clip.getJumpDuration());
        assertTrue(Float.isFinite(clip.getSpeed()));
    }
}

package com.director.timeline.model.entity;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ActorRouteClip extends EntityClip {
    public enum InterpolationMode {
        LINEAR,
        SMOOTH
    }

    public enum LoopMode {
        ONCE,
        LOOP,
        PING_PONG
    }

    private final List<Vector3f> waypoints = new ArrayList<>();
    private InterpolationMode interpolationMode = InterpolationMode.LINEAR;
    private LoopMode loopMode = LoopMode.ONCE;
    private float speed = 2.0f;
    private boolean alignRotationToRoute = true;

    public ActorRouteClip(String name, float startTime, float endTime) {
        super(name, startTime, endTime);
    }

    @Override
    public EntityClipType getClipType() {
        return EntityClipType.ACTOR_ROUTE;
    }

    @Override
    public TimelineClipVisualType getVisualType() {
        return TimelineClipVisualType.CURVE;
    }

    public List<Vector3f> getWaypoints() {
        return Collections.unmodifiableList(waypoints);
    }

    public void setWaypoints(List<Vector3f> points) {
        waypoints.clear();
        if (points == null) {
            return;
        }
        for (Vector3f point : points) {
            if (point != null) {
                waypoints.add(new Vector3f(point));
            }
        }
    }

    public void addWaypoint(float x, float y, float z) {
        waypoints.add(new Vector3f(x, y, z));
    }

    public void setWaypoint(int index, float x, float y, float z) {
        if (index < 0 || index >= waypoints.size()) {
            return;
        }
        waypoints.get(index).set(x, y, z);
    }

    public void removeWaypoint(int index) {
        if (index >= 0 && index < waypoints.size()) {
            waypoints.remove(index);
        }
    }

    public InterpolationMode getInterpolationMode() {
        return interpolationMode;
    }

    public void setInterpolationMode(InterpolationMode interpolationMode) {
        this.interpolationMode = interpolationMode != null ? interpolationMode : InterpolationMode.LINEAR;
    }

    public LoopMode getLoopMode() {
        return loopMode;
    }

    public void setLoopMode(LoopMode loopMode) {
        this.loopMode = loopMode != null ? loopMode : LoopMode.ONCE;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = Math.max(0.05f, speed);
    }

    public boolean isAlignRotationToRoute() {
        return alignRotationToRoute;
    }

    public void setAlignRotationToRoute(boolean alignRotationToRoute) {
        this.alignRotationToRoute = alignRotationToRoute;
    }
}

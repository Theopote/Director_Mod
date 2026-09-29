package com.director.timeline.model.entity;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ActorRouteClip extends EntityClip {
    public enum InterpolationMode {
        /** 折线：锚点之间直线连接 */
        LINEAR,
        /** 平滑：每段使用 smoothstep 缓入缓出 */
        SMOOTH,
        /** Catmull-Rom 样条：经过所有锚点的平滑曲线 */
        CATMULL_ROM
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
    private float jumpStart;
    private float jumpDuration = 0.5f;
    private float jumpHeight;
    private long terrainRevision;
    private String terrainWarning = "";

    public float getJumpStart() { return jumpStart; }
    public void setJumpStart(float value) { jumpStart = Float.isFinite(value) ? Math.max(0, value) : 0; }
    public float getJumpDuration() { return jumpDuration; }
    public void setJumpDuration(float value) { jumpDuration = Float.isFinite(value) ? Math.max(0.1f, Math.min(0.8f, value)) : 0.5f; }
    public float getJumpHeight() { return jumpHeight; }
    public void setJumpHeight(float value) { jumpHeight = Float.isFinite(value) ? Math.max(0, Math.min(1.25f, value)) : 0; }
    public long getTerrainRevision() { return terrainRevision; }
    public void refreshTerrain() { terrainRevision++; terrainWarning = ""; }
    public String getTerrainWarning() { return terrainWarning; }
    public void setTerrainWarning(String warning) { terrainWarning = warning == null ? "" : warning; }

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
        List<Vector3f> copy = new ArrayList<>();
        if (points != null) for (Vector3f point : points) {
            if (point != null && point.isFinite()) copy.add(new Vector3f(point));
        }
        refreshTerrain();
        waypoints.clear();
        waypoints.addAll(copy);
    }

    public void addWaypoint(float x, float y, float z) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) return;
        refreshTerrain();
        waypoints.add(new Vector3f(x, y, z));
    }

    public void setWaypoint(int index, float x, float y, float z) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) return;
        if (index < 0 || index >= waypoints.size()) {
            return;
        }
        waypoints.get(index).set(x, y, z);
        refreshTerrain();
    }

    public void removeWaypoint(int index) {
        if (index >= 0 && index < waypoints.size()) {
            waypoints.remove(index);
            refreshTerrain();
        }
    }

    public InterpolationMode getInterpolationMode() {
        return interpolationMode;
    }

    public void setInterpolationMode(InterpolationMode interpolationMode) {
        refreshTerrain();
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
        this.speed = Float.isFinite(speed) ? Math.max(0.05f, Math.min(20f, speed)) : 2f;
    }

    public boolean isAlignRotationToRoute() {
        return alignRotationToRoute;
    }

    public void setAlignRotationToRoute(boolean alignRotationToRoute) {
        this.alignRotationToRoute = alignRotationToRoute;
    }
}

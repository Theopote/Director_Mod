package com.director.actor.path;

import com.director.timeline.model.entity.ActorRouteClip;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 演员路径定义：独立于时间线的可复用路径资源。
 */
public final class ActorPathDefinition {

    private final UUID id;
    private String name;
    private final List<Vector3f> waypoints = new ArrayList<>();
    private ActorRouteClip.InterpolationMode interpolationMode = ActorRouteClip.InterpolationMode.CATMULL_ROM;
    private boolean locked = false;
    private boolean visible = true;

    public ActorPathDefinition(String name) {
        this(UUID.randomUUID(), name);
    }

    public ActorPathDefinition(UUID id, String name) {
        this.id = Objects.requireNonNull(id, "id");
        setName(name);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        String trimmed = name == null ? "" : name.trim();
        this.name = trimmed.isEmpty() ? "Path" : trimmed;
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

    public ActorRouteClip.InterpolationMode getInterpolationMode() {
        return interpolationMode;
    }

    public void setInterpolationMode(ActorRouteClip.InterpolationMode interpolationMode) {
        this.interpolationMode = interpolationMode != null
                ? interpolationMode
                : ActorRouteClip.InterpolationMode.CATMULL_ROM;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public ActorPathDefinition copy() {
        ActorPathDefinition copy = new ActorPathDefinition(id, name);
        copy.setWaypoints(waypoints);
        copy.setInterpolationMode(interpolationMode);
        copy.setLocked(locked);
        copy.setVisible(visible);
        return copy;
    }
}

package com.chronoblocks.core.timeline;

import java.util.ArrayList;
import java.util.List;

/**
 * Track - A timeline track bound to a target container or property set.
 */
public class Track {
    private String name;
    private final List<Keyframe> keyframes = new ArrayList<>();

    public Track(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Keyframe> getKeyframes() { return keyframes; }

    public void addKeyframe(Keyframe kf) { keyframes.add(kf); }
    public void removeKeyframe(Keyframe kf) { keyframes.remove(kf); }
}

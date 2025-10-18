package com.chronoblocks.core.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A Track binds to a target (block or container) and owns a timeline of Keyframes v4.5
 * <p>支持曲线插值 - sample() 方法自动使用关键帧的插值类型（LINEAR, EASE_IN_OUT, BEZIER 等）
 * 
 * @version 4.5
 */
public class Track {
    private String name = "";                // Optional UI name
    private String targetId = "";            // ID of the bound block/container
    private boolean targetIsContainer = false; // true if targetId refers to a container

    private final List<Keyframe> keyframes = new ArrayList<>();

    public Track() {}

    public Track(String name, String targetId, boolean targetIsContainer) {
        this.name = name != null ? name : "";
        this.targetId = targetId != null ? targetId : "";
        this.targetIsContainer = targetIsContainer;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name != null ? name : ""; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId != null ? targetId : ""; }

    public boolean isTargetContainer() { return targetIsContainer; }
    public void setTargetIsContainer(boolean targetIsContainer) { this.targetIsContainer = targetIsContainer; }

    public List<Keyframe> getKeyframes() { return keyframes; }

    public void addKeyframe(Keyframe kf) {
        if (kf == null) return;
        keyframes.add(new Keyframe(kf));
        sortByTime();
    }

    public boolean removeKeyframeAt(int index) {
        if (index < 0 || index >= keyframes.size()) return false;
        keyframes.remove(index);
        return true;
    }

    public void sortByTime() {
        keyframes.sort(Comparator.comparingDouble(Keyframe::getTime));
    }

    /**
     * Samples this track at time t (seconds) v4.5 - 支持曲线插值
     * <p>使用起始关键帧的插值类型（LINEAR, EASE_IN_OUT, BEZIER 等）进行插值
     * <p>如果超出范围，则钳制到最近的关键帧
     * 
     * @param t 时间（秒）
     * @return 插值后的关键帧
     */
    public Keyframe sample(float t) {
        if (keyframes.isEmpty()) return new Keyframe(t);
        if (keyframes.size() == 1) {
            Keyframe out = new Keyframe(keyframes.get(0));
            out.setTime(t);
            return out;
        }
        sortByTime();

        // Clamp to ends
        if (t <= keyframes.get(0).getTime()) {
            Keyframe out = new Keyframe(keyframes.get(0));
            out.setTime(t);
            return out;
        }
        if (t >= keyframes.get(keyframes.size() - 1).getTime()) {
            Keyframe out = new Keyframe(keyframes.get(keyframes.size() - 1));
            out.setTime(t);
            return out;
        }

        // Find neighbors
        int hi = Collections.binarySearch(keyframes, new Keyframe(t), Comparator.comparingDouble(Keyframe::getTime));
        if (hi < 0) hi = -hi - 1; // first index with time > t
        int lo = hi - 1;
        Keyframe a = keyframes.get(lo);
        Keyframe b = keyframes.get(hi);
        float span = Math.max(1e-6f, b.getTime() - a.getTime());
        float u = (t - a.getTime()) / span;

        Keyframe out = new Keyframe();
        out.setTime(t);
        Keyframe.lerp(a, b, u, out);
        return out;
    }
}

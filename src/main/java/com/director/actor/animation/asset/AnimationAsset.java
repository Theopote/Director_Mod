package com.director.actor.animation.asset;

import com.director.actor.rig.skeleton.RigDefinition;
import com.director.actor.rig.skeleton.RigPose;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Reusable sparse bone animation authored for one rig definition. */
public final class AnimationAsset {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private final String id;
    private String name;
    private final RigDefinition rig;
    private float duration;
    private AnimationLoopMode loopMode;
    private com.director.actor.animation.AnimationData.Purpose purpose = com.director.actor.animation.AnimationData.Purpose.PERFORMANCE;
    private final Map<String, BoneTrack> boneTracks = new LinkedHashMap<>();
    private final List<AnimationMarker> markers = new ArrayList<>();

    public AnimationAsset(String id, String name, RigDefinition rig, float duration, AnimationLoopMode loopMode) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Animation asset id must not be blank");
        if (!Float.isFinite(duration) || duration < 0f) throw new IllegalArgumentException("Duration must be finite and non-negative");
        this.id = id;
        this.name = name == null || name.isBlank() ? id : name;
        this.rig = Objects.requireNonNull(rig, "rig");
        this.duration = duration;
        this.loopMode = loopMode == null ? AnimationLoopMode.ONCE : loopMode;
    }

    public BoneTrack getOrCreateBoneTrack(String boneId) {
        rig.requireBoneIndex(boneId);
        return boneTracks.computeIfAbsent(boneId, ignored -> new BoneTrack());
    }

    public Optional<BoneTrack> findBoneTrack(String boneId) { return Optional.ofNullable(boneTracks.get(boneId)); }

    /** Attach a detached authoring draft only when its transaction is committed. */
    public void attachBoneTrack(String boneId, BoneTrack track) {
        rig.requireBoneIndex(boneId);
        boneTracks.put(boneId, Objects.requireNonNull(track));
    }

    public boolean removeBoneTrack(String boneId) { return boneTracks.remove(boneId) != null; }

    public RigPose sample(float timelineTime) {
        return sample(timelineTime, loopMode);
    }

    public RigPose sample(float timelineTime, AnimationLoopMode playbackMode) {
        float localTime = normalizeTime(timelineTime, playbackMode);
        RigPose result = new RigPose(rig);
        for (Map.Entry<String, BoneTrack> entry : boneTracks.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            int boneIndex = rig.requireBoneIndex(entry.getKey());
            result.setLocalTransform(boneIndex,
                    entry.getValue().sample(localTime, rig.bone(boneIndex).restLocal()),
                    entry.getValue().authoredChannels());
        }
        return result;
    }

    public float normalizeTime(float time) {
        return normalizeTime(time, loopMode);
    }

    public float normalizeTime(float time, AnimationLoopMode playbackMode) {
        float safe = Math.max(0f, time);
        if (duration <= 0f) return 0f;
        AnimationLoopMode mode = playbackMode == null ? loopMode : playbackMode;
        return switch (mode) {
            case ONCE -> Math.min(safe, duration);
            case LOOP -> safe % duration;
            case PING_PONG -> {
                float cycle = safe % (duration * 2f);
                yield cycle <= duration ? cycle : duration * 2f - cycle;
            }
        };
    }

    public void addMarker(AnimationMarker marker) {
        Objects.requireNonNull(marker, "marker");
        if (marker.time() > duration) throw new IllegalArgumentException("Marker lies beyond animation duration");
        markers.add(marker);
        markers.sort(java.util.Comparator.comparingDouble(AnimationMarker::time));
    }

    public int schemaVersion() { return CURRENT_SCHEMA_VERSION; }
    public String id() { return id; }
    public com.director.actor.animation.AnimationData.Purpose purpose() { return purpose; }
    public void setPurpose(com.director.actor.animation.AnimationData.Purpose purpose) { this.purpose=Objects.requireNonNull(purpose); }
    public String name() { return name; }
    public void setName(String name) { this.name = name == null || name.isBlank() ? id : name; }
    public RigDefinition rig() { return rig; }
    public String rigId() { return rig.id(); }
    public float duration() { return duration; }
    public void setDuration(float duration) {
        if (!Float.isFinite(duration) || duration < 0f) throw new IllegalArgumentException("Duration must be finite and non-negative");
        this.duration = duration;
    }
    public AnimationLoopMode loopMode() { return loopMode; }
    public void setLoopMode(AnimationLoopMode loopMode) { this.loopMode = Objects.requireNonNull(loopMode, "loopMode"); }
    public Map<String, BoneTrack> boneTracks() { return Collections.unmodifiableMap(boneTracks); }
    public List<AnimationMarker> markers() { return Collections.unmodifiableList(markers); }
}

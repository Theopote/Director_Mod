package com.chronoblocks.animation.core;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 🎬 AnimationClip - 某个容器在时间线上的片段。
 * 负责描述片段名称、时间范围以及包含的轨道集合。
 */
public class AnimationClip {

    @SerializedName("id")
    private final UUID id = UUID.randomUUID();

    @SerializedName("name")
    private String name;

    @SerializedName("start")
    private double startTimeSec;

    @SerializedName("end")
    private double endTimeSec;

    @SerializedName("tracks")
    private final List<Track<?>> tracks = new ArrayList<>();

    @SerializedName("boundContainerId")
    private UUID boundContainerId;

    public AnimationClip(String name, double startSec, double endSec, UUID containerId) {
        this.name = name != null ? name : "Clip";
        this.startTimeSec = startSec;
        this.endTimeSec = Math.max(startSec, endSec);
        this.boundContainerId = containerId;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name : this.name;
    }

    public double getStart() {
        return startTimeSec;
    }

    public void setStart(double startSec) {
        this.startTimeSec = startSec;
        if (endTimeSec < startTimeSec) {
            endTimeSec = startTimeSec;
        }
    }

    public double getEnd() {
        return endTimeSec;
    }

    public void setEnd(double endSec) {
        this.endTimeSec = Math.max(endSec, startTimeSec);
    }

    public UUID getBoundContainerId() {
        return boundContainerId;
    }

    public void setBoundContainerId(UUID boundContainerId) {
        this.boundContainerId = boundContainerId;
    }

    public List<Track<?>> getTracks() {
        return tracks;
    }

    public void addTrack(Track<?> track) {
        if (track != null && !tracks.contains(track)) {
            tracks.add(track);
        }
    }

    public void removeTrack(Track<?> track) {
        tracks.remove(track);
    }

    /**
     * 获取可见性轨道（v5.3新增）
     */
    public com.chronoblocks.animation.timeline.ContainerVisibilityTrack getVisibilityTrack() {
        for (Track<?> track : tracks) {
            if (track instanceof com.chronoblocks.animation.timeline.ContainerVisibilityTrack) {
                return (com.chronoblocks.animation.timeline.ContainerVisibilityTrack) track;
            }
        }
        return null;
    }
}



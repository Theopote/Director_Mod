package com.director.timeline.model;

import com.director.timeline.model.TimelineSelectable;

import java.util.UUID;

/**
 * 时间线标记，用于分镜或注释。
 */
public class TimelineMarker implements TimelineSelectable {
    private final UUID id;
    private String name;
    private float time;
    private int color = 0xFFBBBBBB;
    private String note;

    public TimelineMarker(String name, float time) {
        this(UUID.randomUUID(), name, time);
    }

    public TimelineMarker(UUID id, String name, float time) {
        this.id = id != null ? id : UUID.randomUUID();
        this.name = name;
        this.time = time;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public float getStartTime() {
        return time;
    }

    @Override
    public float getEndTime() {
        return time;
    }

    @Override
    public String getType() {
        return "Marker";
    }

    public float getTime() {
        return time;
    }

    public void setTime(float time) {
        this.time = time;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}

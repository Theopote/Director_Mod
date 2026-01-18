package com.chronoblocks.ui.timeline.model.entity;

import com.chronoblocks.ui.timeline.model.TimelineClip;

import java.util.Objects;

public class EntityTimelineClip extends TimelineClip {
    private final EntityClip entityClip;
    private boolean syncingFromEntity;
    private boolean syncingToEntity;

    public EntityTimelineClip(EntityClip entityClip, int color) {
        super(
                Objects.requireNonNull(entityClip, "entityClip").getName(),
                entityClip.getStartTime(),
                entityClip.getEndTime(),
                color
        );
        this.entityClip = entityClip;
        this.entityClip.setTimelineClip(this);
        super.setTimeRange(entityClip.getStartTime(), entityClip.getEndTime());
    }

    public EntityClip getEntityClip() {
        return entityClip;
    }

    /**
     * 获取视觉类型（委托给 EntityClip）
     * <p>用于 Timeline 渲染时区分不同的视觉风格
     *
     * @return 视觉类型
     */
    public TimelineClipVisualType getVisualType() {
        return entityClip != null ? entityClip.getVisualType() : TimelineClipVisualType.CURVE;
    }

    void syncFromEntityClip(EntityClip source) {
        if (syncingToEntity) {
            return;
        }
        syncingFromEntity = true;
        try {
            super.setLabel(source.getName());
            super.setTimeRange(source.getStartTime(), source.getEndTime());
        } finally {
            syncingFromEntity = false;
        }
    }

    @Override
    public void setLabel(String label) {
        super.setLabel(label);
        syncEntityName();
    }

    @Override
    public void setTimeRange(float newStart, float newEnd) {
        super.setTimeRange(newStart, newEnd);
        syncEntityTimeRange();
    }

    @Override
    public void setStartTime(float startTime) {
        super.setStartTime(startTime);
        syncEntityTimeRange();
    }

    @Override
    public void setEndTime(float endTime) {
        super.setEndTime(endTime);
        syncEntityTimeRange();
    }

    @Override
    public void setLength(float length) {
        super.setLength(length);
        syncEntityTimeRange();
    }

    @Override
    public void offsetTime(float delta) {
        super.offsetTime(delta);
        syncEntityTimeRange();
    }

    private void syncEntityName() {
        if (syncingFromEntity) {
            return;
        }
        syncingToEntity = true;
        try {
            entityClip.setName(getLabel());
        } finally {
            syncingToEntity = false;
        }
    }

    private void syncEntityTimeRange() {
        if (syncingFromEntity) {
            return;
        }
        syncingToEntity = true;
        try {
            entityClip.setTimeRange(getStartTime(), getEndTime());
        } finally {
            syncingToEntity = false;
        }
    }
}

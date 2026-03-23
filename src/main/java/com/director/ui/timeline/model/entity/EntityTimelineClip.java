package com.director.ui.timeline.model.entity;

import com.director.ui.timeline.model.TimelineClip;
import com.director.ui.timeline.model.clip.ClipKeyframe;

import java.util.List;
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

    /**
     * 同步 EntityClip 的关键帧到 TimelineClip
     * <p>将 EntityClip 的 EntityClipKeyframe 同步为 TimelineClip 的 ClipKeyframe
     *
     * @param source EntityClip 源对象
     */
    void syncKeyframesFromEntityClip(EntityClip source) {
        if (syncingToEntity) {
            return;
        }
        syncingFromEntity = true;
        try {
            // 清空现有关键帧（只清空 ClipKeyframe，保留 Float keyframes 用于兼容）
            List<ClipKeyframe> toRemove =
                new java.util.ArrayList<>(this.getClipKeyframes());
            for (com.director.ui.timeline.model.clip.ClipKeyframe kf : toRemove) {
                this.removeKeyframe(kf.getId());
            }
            
            // 添加 EntityClip 的关键帧（时间已经是全局时间）
            for (EntityClipKeyframe ekf : source.getKeyframes()) {
                com.director.ui.timeline.model.clip.ClipKeyframe kf = 
                    new com.director.ui.timeline.model.clip.ClipKeyframe(
                        ekf.getTime(), 
                        ekf.getInterpolation()
                    );
                this.addKeyframe(kf);
            }
        } finally {
            syncingFromEntity = false;
        }
    }
}

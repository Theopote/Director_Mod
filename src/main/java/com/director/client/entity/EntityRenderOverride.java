package com.director.client.entity;

import com.director.timeline.playback.entity.EntitySample;

/**
 * 演员轨道渲染/查询的只读快照（Mixin 层未来依赖入口）。
 * <p>
 * 由 {@link com.director.timeline.playback.entity.TimelineActorController} 在采样后写入。
 */
public final class EntityRenderOverride {

    private static final EntityRenderOverride INSTANCE = new EntityRenderOverride();

    private volatile boolean overrideActive;
    private volatile EntitySample sample;

    private EntityRenderOverride() {
    }

    public static EntityRenderOverride get() {
        return INSTANCE;
    }

    public boolean hasOverride() {
        return overrideActive && sample != null;
    }

    public EntitySample sample() {
        return overrideActive ? sample : null;
    }

    public static void publish(boolean overrideActive, EntitySample sample) {
        EntityRenderOverride state = INSTANCE;
        state.overrideActive = overrideActive && sample != null;
        state.sample = sample;
    }

    public static void clear() {
        EntityRenderOverride state = INSTANCE;
        state.overrideActive = false;
        state.sample = null;
    }
}

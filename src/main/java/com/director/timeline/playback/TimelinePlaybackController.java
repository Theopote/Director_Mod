package com.director.timeline.playback;

/**
 * 时间线播放子系统的公共协议（World / Season / Weather / Actor / Camera 等控制器共享）。
 *
 * <p>线程模型见 {@link TimelinePlaybackThreading}：写入路径在 synchronized 方法内完成，
 * 渲染/Mixin 应优先读取 {@code *RenderOverride} 快照，而非直接依赖控制器字段。
 *
 * @param <TSample> 当前采样类型（TimeSample、SeasonSample、WeatherSample 等）
 */
public interface TimelinePlaybackController<TSample> {

    boolean isEnabled();

    void setEnabled(boolean enabled);

    /**
     * 是否存在有效 override。热路径实现可使用 volatile 缓存（见 {@link AbstractTimelinePlaybackController}）。
     */
    boolean hasOverride();

    TSample getCurrentSample();

    void previewAt(float timelineTimeSeconds);

    void clear();
}

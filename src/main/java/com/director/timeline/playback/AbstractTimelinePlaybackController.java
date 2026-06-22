package com.director.timeline.playback;

import org.slf4j.Logger;

/**
 * 播放控制器共享状态与同步策略基类。
 *
 * @param <TSample> 采样类型
 */
public abstract class AbstractTimelinePlaybackController<TSample>
        implements TimelinePlaybackController<TSample> {

    protected final Logger logger;

    /** 是否启用轨道接管 */
    protected boolean enabled;

    /** 当前采样结果 */
    protected TSample currentSample;

    /**
     * 缓存的 override 标志；在 synchronized 写入路径末尾刷新，供 {@link #hasOverride()} 无锁读取。
     */
    protected volatile boolean cachedHasOverride;

    protected AbstractTimelinePlaybackController(Logger logger, boolean enabledByDefault) {
        this.logger = logger;
        this.enabled = enabledByDefault;
    }

    protected abstract String logTag();

  @Override
    public synchronized void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (!enabled) {
            onDisabled();
        } else {
            logger.info("[{}] 轨道接管已启用", logTag());
        }
        commitState();
    }

    @Override
    public synchronized boolean isEnabled() {
        return enabled;
    }

    /**
     * 热路径：无锁读缓存。写入方必须在 {@link #commitState()} 前完成全部字段更新。
     */
    @Override
    public boolean hasOverride() {
        return cachedHasOverride;
    }

    @Override
    public synchronized TSample getCurrentSample() {
        return currentSample;
    }

    /**
     * 在锁内更新 sample 并提交（刷新缓存 + publish 快照）。
     */
    protected synchronized void applySample(TSample sample) {
        currentSample = sample;
        commitState();
        onSampleApplied(sample);
    }

    /**
     * 清除 override 采样，保留 enabled / 轨道绑定。
     */
    protected synchronized void clearOverride() {
        currentSample = null;
        commitState();
        onOverrideCleared(false);
    }

    /**
     * 禁用或关闭 Director 时的完整清理（子类扩展）。
     */
    @Override
    public synchronized void clear() {
        enabled = false;
        currentSample = null;
        activeTrackCleared();
        commitState();
        onOverrideCleared(true);
        logger.info("[{}] Cleared override state", logTag());
    }

    /**
     * 刷新 {@link #cachedHasOverride} 并 publish 渲染快照。必须在持有控制器锁时调用。
     */
    protected void commitState() {
        cachedHasOverride = computeHasOverride();
        publishRenderSnapshot();
    }

    /**
     * 计算 override 是否有效（仅在持有锁时调用）。
     */
    protected abstract boolean computeHasOverride();

    /**
     * 将当前状态 publish 到 {@code WorldRenderOverride} / {@code CameraRenderOverride} 等只读门面。
     */
    protected abstract void publishRenderSnapshot();

    /** {@code setEnabled(false)} 时清理副作用 */
    protected abstract void onDisabled();

    /** sample 应用后的子系统分发 */
    protected abstract void onSampleApplied(TSample sample);

    /**
     * override 清除：{@code fullReset=true} 表示 {@link #clear()}，否则仅 {@link #clearOverride()}。
     */
    protected abstract void onOverrideCleared(boolean fullReset);

    /** {@link #clear()} 时清空轨道绑定（轨道型控制器覆写） */
    protected void activeTrackCleared() {
    }
}

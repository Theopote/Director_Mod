package com.director.timeline.playback.season;

import com.director.client.world.WorldRenderOverride;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.playback.AbstractModelTimelinePlaybackController;
import com.director.timeline.playback.TimelinePlaybackThreading;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 季节轨道播放控制器。
 *
 * @see TimelinePlaybackThreading
 */
public final class TimelineSeasonController extends AbstractModelTimelinePlaybackController<SeasonSample> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineSeasonController.class);
    private static final TimelineSeasonController INSTANCE = new TimelineSeasonController();

    private static final long RERENDER_THROTTLE_MS = 100;

    private final SeasonTrackSampler sampler = new SeasonTrackSampler();
    private long lastRerenderTimestamp = 0;
    private boolean rerenderPending;
    private Float previewTime;

    private TimelineSeasonController() {
        super(LOGGER, true);
        currentSample = SeasonSample.disabled();
    }

    public static TimelineSeasonController getInstance() {
        return INSTANCE;
    }

    @Override
    protected String logTag() {
        return "TimelineSeason";
    }

    @Override
    protected TrackType expectedTrackType() {
        return TrackType.SEASON;
    }

    public TimelineTrack getActiveSeasonTrack() {
        return getActiveTrack();
    }

    public void setActiveSeasonTrack(TimelineTrack track) {
        previewTime = null;
        activateTrack(track);
    }

    @Override
    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
        previewTime = null;
        if (enabled) rebuildFromActiveTrack();
        else {
            setCurrentSample(SeasonSample.disabled());
            cleanupSeasonEffects();
        }
    }

    @Override
    protected void onModelDetached() {
        previewTime = null;
        sampler.clear();
        setCurrentSample(SeasonSample.disabled());
        cleanupSeasonEffects();
    }

    @Override
    protected void onModelAttached(TimelineModel model) {
        logger.info("[TimelineSeason] init 完成，activeSeasonTrack = {}",
                activeTrack != null ? activeTrack.getName() : "<none>");
    }

    @Override
    protected void onModelStructureChanged() {
        if (timelineModel == null) {
            logger.warn("[TimelineSeason] onModelStructureChanged() - timelineModel 为 null，跳过处理");
            return;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("[TimelineSeason] onModelStructureChanged() - 开始处理模型结构变更");
        }
        super.onModelStructureChanged();
    }

    @Override
    protected void onModelStructureChangedComplete() {
        if (logger.isDebugEnabled()) {
            logger.debug("[TimelineSeason] 模型变更完成，激活轨道: {}, 关键帧数: {}",
                    activeTrack != null ? activeTrack.getName() : "<none>",
                    sampler.hasKeyframes() ? "有" : "无");
        }
    }

    @Deprecated
    public synchronized void update() {
        if (!enabled || timelineModel == null || !isValidTrack(activeTrack)) {
            return;
        }
        if (!timelineModel.isPlaying() && !timelineModel.isScrubbing()) {
            return;
        }
        onTimelineTick(currentTimelineTime());
    }

    public synchronized void onTimelineTick(float timelineTime) {
        previewTime = null;
        if (!enabled || timelineModel == null) {
            if (currentSample == null || !currentSample.enabled) {
                return;
            }
            setCurrentSample(SeasonSample.disabled());
            propagateToWorld();
            return;
        }

        if (!isValidTrack(activeTrack) || sampler.isEmpty()) {
            if (currentSample == null || !currentSample.enabled) {
                return;
            }
            setCurrentSample(SeasonSample.disabled());
            propagateToWorld();
            return;
        }

        SeasonSample sample = sampler.sampleAt(timelineTime);
        if (currentSample != null && currentSample.equals(sample)) {
            return;
        }
        setCurrentSample(sample);
        propagateToWorld();
    }

    @Deprecated
    public synchronized void update(float currentTime) {
        onTimelineTick(currentTime);
    }

    @Override
    public synchronized void previewAt(float timeSeconds) {
        previewTime = Float.isFinite(timeSeconds) ? Math.max(0, timeSeconds) : 0;
        if (!enabled || !isValidTrack(activeTrack)) {
            return;
        }
        SeasonSample sample = sampler.sampleAt(timeSeconds);
        if (sample.enabled) {
            setCurrentSample(sample);
            propagateToWorld();
            logger.debug("[TimelineSeason] 预览季节 @ {}s: {}", timeSeconds, sample);
        }
    }

    public synchronized void previewAtKeyframeTime(float keyframeTime) {
        previewAt(keyframeTime);
    }

    @Override
    public synchronized void rebuildFromActiveTrack() {
        if (!isValidTrack(activeTrack)) {
            sampler.clear();
            setCurrentSample(SeasonSample.disabled());
            propagateToWorld();
            cleanupSeasonEffects();
            return;
        }

        sampler.setFromTrack(activeTrack);

        if (enabled && timelineModel != null && !sampler.isEmpty()) {
            setCurrentSample(sampler.sampleAt(previewTime != null ? previewTime : currentTimelineTime()));
            propagateToWorld();
            logger.debug("[TimelineSeason] 重新构建轨道，已更新当前时间点的季节状态");
        } else {
            setCurrentSample(SeasonSample.disabled());
            propagateToWorld();
            cleanupSeasonEffects();
        }

        if (logger.isDebugEnabled()) {
            logger.debug("[TimelineSeason] Rebuild from track '{}', keyframes = {}",
                    activeTrack != null ? activeTrack.getName() : "<none>",
                    sampler.getKeyframes().size());
        }
    }

    @Override
    public synchronized SeasonSample getCurrentSample() {
        if (currentSample == null) {
            currentSample = SeasonSample.disabled();
        }
        return currentSample;
    }

    @Deprecated
    public synchronized SeasonSample current() {
        return getCurrentSample();
    }

    @Override
    public synchronized void clear() {
        init(null);
        sampler.clear();
        setCurrentSample(SeasonSample.disabled());
        cleanupSeasonEffects();
    }

    @Override
    protected boolean computeHasOverride() {
        if (!enabled || !isValidTrack(activeTrack) || !sampler.hasKeyframes() || currentSample == null || !currentSample.enabled) {
            return false;
        }
        return currentSample.hasAnyEffect();
    }

    @Override
    protected void publishRenderSnapshot() {
        WorldRenderOverride.publishSeason(hasOverride(), currentSample);
    }

    @Override
    protected void onDisabled() {
        currentSample = SeasonSample.disabled();
    }

    @Override
    protected void onSampleApplied(SeasonSample sample) {
        propagateToWorld();
    }

    @Override
    protected void onOverrideCleared(boolean fullReset) {
        if (fullReset) {
            cleanupSeasonEffects();
        }
    }

    private void setCurrentSample(SeasonSample sample) {
        SeasonSample before = currentSample;
        currentSample = sample;
        commitState();
        if (before == null || !before.grassTint.equals(sample.grassTint) || !before.foliageTint.equals(sample.foliageTint)) {
            rerenderPending = true;
            com.director.season.SeasonWorldEffects.terrainChanged();
        }
        flushRerender(false);
    }

    private void propagateToWorld() {
        if (hasOverride() && currentSample != null) {
            try {
                com.director.season.SeasonSnowManager.get().onSeasonSampleUpdated(currentSample);
            } catch (Exception e) {
                logger.warn("[TimelineSeason] Failed to propagate to SeasonSnowManager", e);
            }
            try {
                com.director.season.WaterFreezeManager.get().applySeasonSample(currentSample);
            } catch (Exception e) {
                logger.warn("[TimelineSeason] Failed to propagate to WaterFreezeManager", e);
            }
        } else {
            try {
                com.director.season.SeasonSnowManager.get().onSeasonSampleUpdated(null);
            } catch (Exception e) {
                logger.warn("[TimelineSeason] Failed to clear SeasonSnowManager", e);
            }
            try {
                com.director.season.WaterFreezeManager.get().applySeasonSample(null);
            } catch (Exception e) {
                logger.warn("[TimelineSeason] Failed to clear WaterFreezeManager", e);
            }
        }
    }

    /** Keep the final tint request pending when updates arrive inside the throttle window. */
    public synchronized void flushRerender(boolean force) {
        if (!rerenderPending) return;
        long now = System.currentTimeMillis();
        if (!force && now - lastRerenderTimestamp < RERENDER_THROTTLE_MS) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.worldRenderer == null) return;
        var storage = ((com.director.mixin.accessor.LightWorldRendererAccessor)client.worldRenderer).director$getLightChunkStorage();
        if (storage != null) for (var chunk : storage.chunks) chunk.scheduleRebuild(false);
        lastRerenderTimestamp = now;
        rerenderPending = false;
    }

    private void cleanupSeasonEffects() {
        com.director.season.SeasonSnowManager.get().shutdown();
        com.director.season.WaterFreezeManager.get().clearAll();
        com.director.season.SeasonWorldEffects.terrainChanged();
        flushRerender(true);
    }
}

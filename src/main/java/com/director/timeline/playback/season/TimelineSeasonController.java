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

    private TimelineSeasonController() {
        super(LOGGER, true);
        currentSample = SeasonSample.disabled();
    }

    public static TimelineSeasonController getInstance() {
        return INSTANCE;
    }

    public static TimelineSeasonController get() {
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
        activateTrack(track);
    }

    @Override
    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            currentSample = SeasonSample.disabled();
        }
        commitState();
        logger.info("[TimelineSeason] 季节控制 {}", enabled ? "已启用" : "已禁用");
    }

    @Override
    protected void onModelDetached() {
        sampler.clear();
        currentSample = SeasonSample.disabled();
        commitState();
        logger.info("[TimelineSeason] init(null) → 关闭季节控制");
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
        if (!enabled || !isValidTrack(activeTrack)) {
            return;
        }
        SeasonSample sample = sampler.sampleAt(keyframeTime);
        if (sample.enabled) {
            setCurrentSample(sample);
            propagateToWorld();
            logger.debug("[TimelineSeason] 预览关键帧季节 @ {}s: {}", keyframeTime, sample);
        }
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

        if (timelineModel != null && !sampler.isEmpty()) {
            setCurrentSample(sampler.sampleAt(currentTimelineTime()));
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
        sampler.clear();
        setCurrentSample(SeasonSample.disabled());
        propagateToWorld();
        cleanupSeasonEffects();
        logger.info("[TimelineSeason] Cleared season override state - 恢复游戏默认渲染");
    }

    @Override
    protected boolean computeHasOverride() {
        if (!enabled || !isValidTrack(activeTrack) || !sampler.hasKeyframes() || currentSample == null) {
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
        currentSample = sample;
        commitState();
        scheduleThrottledRerender();
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

    private void scheduleThrottledRerender() {
        long now = System.currentTimeMillis();
        if (now - lastRerenderTimestamp < RERENDER_THROTTLE_MS) {
            return;
        }
        lastRerenderTimestamp = now;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.worldRenderer == null) {
            return;
        }
        try {
            net.minecraft.util.math.BlockPos playerPos = client.player != null
                    ? client.player.getBlockPos()
                    : new net.minecraft.util.math.BlockPos(0, 64, 0);
            int renderDistance = client.player != null
                    ? Math.max(client.options.getViewDistance().getValue(), 8)
                    : 8;
            int chunkRadius = renderDistance * 16;
            int minX = playerPos.getX() - chunkRadius;
            int minY = Math.max(playerPos.getY() - 64, -64);
            int minZ = playerPos.getZ() - chunkRadius;
            int maxX = playerPos.getX() + chunkRadius;
            int maxY = Math.min(playerPos.getY() + 256, 320);
            int maxZ = playerPos.getZ() + chunkRadius;
            client.worldRenderer.scheduleBlockRenders(minX, minY, minZ, maxX, maxY, maxZ);
            if (logger.isDebugEnabled()) {
                logger.debug("[TimelineSeason] 已调度区域重渲染 (节流) - hasOverride() = {}", cachedHasOverride);
            }
        } catch (Exception e) {
            logger.warn("[TimelineSeason] 调度区域重渲染失败: {}", e.getMessage(), e);
        }
    }

    private void cleanupSeasonEffects() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.world != null) {
                com.director.season.SeasonSnowManager.get().onSeasonSampleUpdated(null);
                com.director.season.WaterFreezeManager.get().applySeasonSample(null);
                com.director.season.SeasonSnowManager.get().restoreAll(client.world);
                com.director.season.WaterFreezeManager.get().clearAll();
                logger.info("[TimelineSeason] 已清理所有季节效果（积雪 + 冰冻水面）");
            }
        } catch (Exception e) {
            logger.warn("[TimelineSeason] 清理季节效果时出错", e);
        }
    }
}

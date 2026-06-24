package com.director.timeline.playback;

import com.director.core.DirectorServices;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import org.slf4j.Logger;

/**
 * 仅绑定单条激活轨道的播放控制器基类（Weather、Actor）。
 */
public abstract class AbstractTrackTimelinePlaybackController<TSample>
        extends AbstractTimelinePlaybackController<TSample> {

    protected TimelineTrack activeTrack;

    protected AbstractTrackTimelinePlaybackController(Logger logger) {
        super(logger, false);
    }

    protected abstract TrackType expectedTrackType();

    public synchronized void bindActiveTrack(TimelineTrack track) {
        if (track != null && track.getType() != expectedTrackType()) {
            logger.warn("[{}] 试图绑定非{}轨道: {}", logTag(), expectedTrackType(), track.getType());
        }
        this.activeTrack = track;
        rebuildFromActiveTrack();
    }

    public synchronized TimelineTrack getActiveTrack() {
        return activeTrack;
    }

    /**
     * 校验目标轨道是否可在当前激活轨道上添加关键帧。
     */
    public synchronized boolean isAddKeyframeTargetValid(TimelineTrack track) {
        return TrackKeyframeEditSupport.validateAddTarget(
                track, activeTrack, expectedTrackType(), logger, logTag());
    }

    /**
     * 模型中是否存在本类型轨道（子类可扩展判定逻辑）。
     */
    public synchronized boolean hasTrackInModel() {
        if (activeTrack != null) {
            return true;
        }
        TimelineModel model = resolveTimelineModel();
        if (model == null) {
            return false;
        }
        for (TimelineTrack track : model.getTracks()) {
            if (track.getType() == expectedTrackType()) {
                return true;
            }
        }
        return false;
    }

    public synchronized void rebuildFromActiveTrack() {
        if (activeTrack == null) {
            disposeSampler();
            currentSample = null;
            commitState();
            return;
        }

        rebuildSampler(activeTrack);
        int keyframeCount = countTrackKeyframes(activeTrack);
        logger.debug("[{}] 重建采样器，关键帧数 = {}", logTag(), keyframeCount);
        onRebuildComplete(keyframeCount);
        commitState();
    }

    /**
     * 模型结构变更时：单轨道自动激活、多轨道校验、无轨道时禁用。
     */
    public synchronized void checkAndAutoActivate() {
        TimelineModel model = resolveTimelineModel();
        if (model == null) {
            return;
        }

        int trackCount = 0;
        TimelineTrack firstTrack = null;
        for (TimelineTrack track : model.getTracks()) {
            if (track.getType() == expectedTrackType()) {
                trackCount++;
                if (firstTrack == null) {
                    firstTrack = track;
                }
            }
        }

        if (trackCount == 1) {
            if (activeTrack != firstTrack) {
                activeTrack = firstTrack;
                logger.info("[{}] 检测到只有一个{}轨道，自动激活: {}",
                        logTag(), expectedTrackType(), firstTrack.getName());
                rebuildFromActiveTrack();
            }
            if (!enabled) {
                setEnabled(true);
            }
            onSingleTrackAutoActivated();
        } else if (trackCount == 0) {
            if (activeTrack != null) {
                activeTrack = null;
                disposeSampler();
                currentSample = null;
                logger.info("[{}] 没有{}轨道，清空激活轨道", logTag(), expectedTrackType());
                commitState();
            }
            if (enabled) {
                setEnabled(false);
            }
        } else {
            if (activeTrack != null && !model.getTracks().contains(activeTrack)) {
                activeTrack = firstTrack;
                logger.info("[{}] 当前激活轨道已被删除，切换到: {}", logTag(), firstTrack.getName());
                rebuildFromActiveTrack();
            }
            if (activeTrack != null && !enabled) {
                setEnabled(true);
            }
        }
        commitState();
    }

    public synchronized void updateForPlayback(float timelineTimeSeconds) {
        if (!enabled || activeTrack == null || !hasActiveSampler()) {
            clearOverride();
            return;
        }
        applySample(sampleAt(timelineTimeSeconds));
    }

    @Override
    public synchronized void previewAt(float timelineTimeSeconds) {
        updateForPlayback(timelineTimeSeconds);
    }

    @Deprecated
    public void onTimelineTick(float timelineTime) {
        updateForPlayback(timelineTime);
    }

    @Override
    protected void activeTrackCleared() {
        activeTrack = null;
        disposeSampler();
    }

    @Override
    protected boolean computeHasOverride() {
        return enabled && activeTrack != null && hasActiveSampler() && currentSample != null;
    }

    protected static float resolveCurrentTimelineTime() {
        var service = DirectorServices.get().containerTimeline();
        if (service == null) {
            return 0f;
        }
        TimelineModel model = service.getTimelineModel();
        return model != null ? model.getCurrentTime() : 0f;
    }

    protected static TimelineModel resolveTimelineModel() {
        var service = DirectorServices.get().containerTimeline();
        if (service == null) {
            return null;
        }
        return service.getTimelineModel();
    }

    protected abstract void rebuildSampler(TimelineTrack track);

    protected abstract void disposeSampler();

    protected abstract boolean hasActiveSampler();

    protected abstract int countTrackKeyframes(TimelineTrack track);

    protected abstract TSample sampleAt(float timelineTimeSeconds);

    /** 重建采样器后（有关键帧时可选立即采样） */
    protected void onRebuildComplete(int keyframeCount) {
    }

    /** 仅一条轨道时激活后的额外逻辑 */
    protected void onSingleTrackAutoActivated() {
    }
}

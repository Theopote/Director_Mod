package com.director.timeline.playback;

import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import org.slf4j.Logger;

/**
 * 绑定 {@link TimelineModel} 的播放控制器基类（World、Season、Camera 等）。
 *
 * <p>提供模型监听、激活轨道自动选取、结构变更时的轨道校验。
 * 子类可覆写 {@link #clear()}、{@link #setEnabled(boolean)} 以实现不同的清理语义。
 */
public abstract class AbstractModelTimelinePlaybackController<TSample>
        extends AbstractTimelinePlaybackController<TSample> {

    protected TimelineModel timelineModel;
    protected TimelineTrack activeTrack;
    protected TimelineModel.ModelChangeListener modelChangeListener;

    protected AbstractModelTimelinePlaybackController(Logger logger, boolean enabledByDefault) {
        super(logger, enabledByDefault);
    }

    protected abstract TrackType expectedTrackType();

    /**
     * 绑定 / 切换时间线模型。
     */
    public synchronized void init(TimelineModel model) {
        if (this.timelineModel == model) {
            return;
        }

        detachModelListener();

        this.timelineModel = model;

        if (model == null) {
            activeTrack = null;
            onModelDetached();
            return;
        }

        ensureModelListener();
        model.addModelChangeListener(modelChangeListener);

        if (!isValidTrack(activeTrack)) {
            activeTrack = findFirstTrackOfType();
            if (countTracksOfType() == 1 && activeTrack != null) {
                onSingleTrackAutoActivatedOnInit(activeTrack);
            }
        }

        rebuildFromActiveTrack();
        onModelAttached(model);
    }

    @Deprecated
    public synchronized void setTimelineModel(TimelineModel model) {
        init(model);
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
     * 切换激活轨道（子类可暴露类型化别名，如 {@code setActiveSeasonTrack}）。
     */
    protected synchronized void activateTrack(TimelineTrack track) {
        if (track != null && track.getType() != expectedTrackType()) {
            logger.warn("[{}] 非 {} 轨道被选中，忽略: {}",
                    logTag(), expectedTrackType(), track != null ? track.getName() : "<null>");
            return;
        }
        if (track == activeTrack) {
            return;
        }
        this.activeTrack = track;
        logger.info("[{}] 激活轨道: {}", logTag(), track != null ? track.getName() : "<none>");
        rebuildFromActiveTrack();
    }

    protected synchronized void onModelStructureChanged() {
        if (timelineModel == null) {
            return;
        }

        int trackCount = countTracksOfType();
        TimelineTrack firstTrack = findFirstTrackOfType();

        if (trackCount == 1 && firstTrack != null) {
            if (activeTrack != firstTrack) {
                activeTrack = firstTrack;
                onSingleTrackAutoActivatedOnModelChange(firstTrack);
            }
        } else if (trackCount == 0) {
            activeTrack = null;
        } else if (activeTrack != null && !isTrackInModel(activeTrack)) {
            activeTrack = firstTrack;
            onActiveTrackRemovedFromModel(firstTrack);
        } else if (!isValidTrack(activeTrack)) {
            activeTrack = firstTrack;
        }

        rebuildFromActiveTrack();
        onModelStructureChangedComplete();
    }

    protected boolean isValidTrack(TimelineTrack track) {
        if (track == null || track.getType() != expectedTrackType()) {
            return false;
        }
        return timelineModel != null && timelineModel.getTracks().contains(track);
    }

    protected TimelineTrack findFirstTrackOfType() {
        if (timelineModel == null) {
            return null;
        }
        for (TimelineTrack track : timelineModel.getTracks()) {
            if (track.getType() == expectedTrackType()) {
                return track;
            }
        }
        return null;
    }

    protected int countTracksOfType() {
        if (timelineModel == null) {
            return 0;
        }
        int count = 0;
        for (TimelineTrack track : timelineModel.getTracks()) {
            if (track.getType() == expectedTrackType()) {
                count++;
            }
        }
        return count;
    }

    protected boolean isTrackInModel(TimelineTrack track) {
        return track != null && timelineModel != null && timelineModel.getTracks().contains(track);
    }

    protected float currentTimelineTime() {
        return timelineModel != null ? timelineModel.getCurrentTime() : 0f;
    }

    @Override
    protected void activeTrackCleared() {
        activeTrack = null;
    }

    private void detachModelListener() {
        if (timelineModel != null && modelChangeListener != null) {
            timelineModel.removeModelChangeListener(modelChangeListener);
        }
    }

    private void ensureModelListener() {
        if (modelChangeListener == null) {
            modelChangeListener = changeType -> onModelStructureChanged();
        }
    }

    /** {@code init(null)} 时调用 */
    protected void onModelDetached() {
    }

    /** {@code init(model)} 完成后调用 */
    protected void onModelAttached(TimelineModel model) {
    }

    protected void onSingleTrackAutoActivatedOnInit(TimelineTrack track) {
        logger.info("[{}] 检测到只有一个{}轨道，自动激活: {}",
                logTag(), expectedTrackType(), track.getName());
    }

    protected void onSingleTrackAutoActivatedOnModelChange(TimelineTrack track) {
        if (logger.isDebugEnabled()) {
            logger.debug("[{}] 模型变更：自动激活轨道: {}", logTag(), track.getName());
        }
    }

    protected void onActiveTrackRemovedFromModel(TimelineTrack fallback) {
        if (logger.isDebugEnabled()) {
            logger.debug("[{}] 激活轨道已删除，切换到: {}",
                    logTag(), fallback != null ? fallback.getName() : "<none>");
        }
    }

    protected void onModelStructureChangedComplete() {
    }

    public abstract void rebuildFromActiveTrack();
}

package com.director.timeline.playback.entity;

import com.director.client.DirectorClientController;
import com.director.timeline.model.TimelineSelectable;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.entity.EntityKeyframe;
import com.director.client.entity.EntityRenderOverride;
import com.director.timeline.playback.AbstractTrackTimelinePlaybackController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 演员轨道播放控制器：采样 ActorTrackSampler 并驱动实体表现。
 *
 * <p>线程模型见 {@link com.director.timeline.playback.TimelinePlaybackThreading}。
 */
public final class TimelineActorController extends AbstractTrackTimelinePlaybackController<EntitySample> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineActorController.class);
    private static final TimelineActorController INSTANCE = new TimelineActorController();

    private ActorTrackSampler sampler;

    private TimelineActorController() {
        super(LOGGER);
    }

    public static TimelineActorController getInstance() {
        return INSTANCE;
    }

    @Override
    protected String logTag() {
        return "ActorController";
    }

    @Override
    protected TrackType expectedTrackType() {
        return TrackType.ACTOR;
    }

    @Deprecated
    public void setActiveActorTrack(TimelineTrack track) {
        bindActiveTrack(track);
    }

    public TimelineTrack getActiveActorTrack() {
        return getActiveTrack();
    }

    @Override
    public synchronized boolean hasTrackInModel() {
        if (DirectorClientController.getInstance().isDirectorModeActive()) {
            return true;
        }
        return super.hasTrackInModel();
    }

    public boolean hasActorTrack() {
        return hasTrackInModel();
    }

    @Override
    protected void rebuildSampler(TimelineTrack track) {
        sampler = new ActorTrackSampler(track);
        sampler.rebuildKeyframeList();
    }

    @Override
    protected void disposeSampler() {
        sampler = null;
    }

    @Override
    protected boolean hasActiveSampler() {
        return sampler != null;
    }

    @Override
    protected int countTrackKeyframes(TimelineTrack track) {
        int count = 0;
        for (TimelineSelectable selectable : track.getAutomationKeyframes()) {
            if (selectable instanceof EntityKeyframe) {
                count++;
            }
        }
        return count;
    }

    @Override
    protected EntitySample sampleAt(float timelineTimeSeconds) {
        return sampler.sample(timelineTimeSeconds);
    }

    @Override
    protected void onDisabled() {
        currentSample = null;
        logger.info("[ActorController] 清除实体 override，并尝试恢复 Vanilla 实体状态");
    }

    @Override
    protected void onSampleApplied(EntitySample sample) {
        if (sample == null || !sample.enabled) {
            return;
        }
        logger.trace("[ActorController] 应用实体采样: position={}, rotation={}, action={}",
                sample.position, sample.rotation, sample.actionType);
    }

    @Override
    protected void onOverrideCleared(boolean fullReset) {
        // 实体子系统清理待实现
    }

    @Override
    protected void publishRenderSnapshot() {
        EntityRenderOverride.publish(hasOverride(), currentSample);
    }
}

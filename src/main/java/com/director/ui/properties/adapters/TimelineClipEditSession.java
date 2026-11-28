package com.director.ui.properties.adapters;

import com.director.core.animation.WholeContainerAnimation;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.ui.timeline.audio.TimelineAudioService;
import com.director.ui.timeline.commands.StateChangeCommand;
import com.director.ui.timeline.commands.TimelineCommandBridge;
import java.util.function.Consumer;

/** Groups live clip property previews into one undoable editor gesture. */
public final class TimelineClipEditSession {
    private record State(TimelineClip snapshot, Consumer<TimelineClip> refreshSound) {
        void apply(TimelineModel model, TimelineClip clip) {
            if (model.findTrackForClip(clip) == null)
                throw new IllegalStateException("Edited clip is no longer in this timeline");
            var config = clip.getAnimationConfigForEditing();
            float oldDuration = config.getDuration();
            float oldStart = clip.getStartTime();
            float oldEnd = clip.getEndTime();
            boolean retime = config.isWholeContainer() && snapshot.getAnimationConfigForEditing().isWholeContainer()
                    && Float.compare(config.getDelay(), snapshot.getAnimationConfigForEditing().getDelay()) == 0;
            clip.restoreStateFrom(snapshot);
            var audio = TimelineAudioService.getInstance();
            if (audio.getTimelineModel() == model && (oldStart != clip.getStartTime() || oldEnd != clip.getEndTime())) {
                audio.onClipTimeRangeChanged(clip, oldStart, oldEnd, clip.getStartTime(), clip.getEndTime());
            }
            if (retime) model.setCurrentTime(WholeContainerAnimation.retimePreviewTime(
                    clip.getStartTime(), clip.getEndTime(), config.getDelay(), oldDuration,
                    config.getDuration(), model.getCurrentTime()));
            refreshSound.accept(clip);
        }
    }

    private final Consumer<TimelineClip> refreshSound;
    private TimelineModel frameModel, pendingModel;
    private TimelineClip frameClip, pendingClip;
    private com.director.core.animation.ClipAnimationConfig pendingConfig;
    private State frameBefore, pendingBefore;
    private String pendingDescription;

    public TimelineClipEditSession(Consumer<TimelineClip> refreshSound) {
        this.refreshSound = refreshSound;
    }

    public void beginFrame(TimelineModel model, TimelineClip clip) {
        if (pendingClip != null && pendingModel != model) clearPending();
        if (pendingClip != null && pendingClip != clip) finishIfIdle(false);
        if (pendingClip != null && pendingClip.getAnimationConfigForEditing() != pendingConfig) clearPending();
        frameModel = model;
        frameClip = clip;
        frameBefore = clip != null && model != null && model.findTrackForClip(clip) != null
                ? new State(clip.snapshot(), refreshSound) : null;
    }

    public void endFrame(boolean modified, boolean itemActive, String description) {
        if (modified && frameBefore != null && frameModel != null
                && frameModel.findTrackForClip(frameClip) != null && pendingClip == null) {
            pendingClip = frameClip;
            pendingModel = frameModel;
            pendingConfig = frameClip.getAnimationConfigForEditing();
            pendingBefore = frameBefore;
            pendingDescription = description;
        }
        finishIfIdle(itemActive);
    }

    private void clearPending() {
        pendingClip = null;
        pendingModel = null;
        pendingConfig = null;
        pendingBefore = null;
        pendingDescription = null;
    }

    public void finishIfIdle(boolean itemActive) {
        if (itemActive || pendingClip == null) return;
        var clip = pendingClip;
        var model = pendingModel;
        var config = pendingConfig;
        var before = pendingBefore;
        var description = pendingDescription;
        clearPending();
        if (model.findTrackForClip(clip) == null || clip.getAnimationConfigForEditing() != config) return;
        var after = new State(clip.snapshot(), refreshSound);
        if (before.snapshot.hasSameStateAs(after.snapshot)) return;
        TimelineCommandBridge.execute(new StateChangeCommand(model, description,
                () -> before.apply(model, clip), () -> after.apply(model, clip)));
    }

    public void cancel() {
        if (pendingClip == null) return;
        var clip = pendingClip;
        var model = pendingModel;
        var config = pendingConfig;
        var before = pendingBefore;
        clearPending();
        if (model.findTrackForClip(clip) == null || clip.getAnimationConfigForEditing() != config) return;
        before.apply(model, clip);
    }
}

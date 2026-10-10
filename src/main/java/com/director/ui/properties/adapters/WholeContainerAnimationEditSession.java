package com.director.ui.properties.adapters;

import com.director.core.animation.ClipAnimationConfig;
import com.director.core.animation.WholeContainerAnimation;
import com.director.core.animation.WholeContainerAnimationConfig;
import com.director.core.container.AnimationMode;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.ui.timeline.commands.StateChangeCommand;
import com.director.ui.timeline.commands.TimelineCommandBridge;
import java.util.function.Consumer;

/** Groups live whole-animation property previews into one undoable editor gesture. */
public final class WholeContainerAnimationEditSession {
    private record Transform(WholeContainerAnimationConfig.Effect effect,
                             WholeContainerAnimationConfig.Pivot pivot,
                             float scale, float x, float y, float z,
                             float rx, float ry, float rz, float px, float py, float pz,
                             boolean translation, boolean rotation, boolean scaling, boolean fading, float opacity,
                             WholeContainerAnimationConfig.Motion motion, float strength, int cycles, float height) {
        static Transform capture(WholeContainerAnimationConfig c) {
            return new Transform(c.getEffect(), c.getPivot(), c.getScale(), c.getOffsetX(),
                    c.getOffsetY(), c.getOffsetZ(), c.getRotationX(), c.getRotationY(),
                    c.getRotationZ(), c.getPivotX(), c.getPivotY(), c.getPivotZ(),
                    c.isTranslationEnabled(), c.isRotationEnabled(), c.isScaleEnabled(), c.isOpacityEnabled(), c.getOpacity(), c.getMotion(), c.getMotionStrength(), c.getMotionCycles(), c.getBounceHeight());
        }
        void apply(WholeContainerAnimationConfig c) {
            c.setEffect(effect); c.setPivot(pivot); c.setScale(scale);
            c.setOffset(x, y, z); c.setRotation(rx, ry, rz); c.setCustomPivot(px, py, pz);
            c.setTranslationEnabled(translation); c.setRotationEnabled(rotation);
            c.setScaleEnabled(scaling); c.setOpacityEnabled(fading); c.setOpacity(opacity);
            c.setMotion(motion); c.setMotionStrength(strength); c.setMotionCycles(cycles); c.setBounceHeight(height);
        }
    }
    private record State(ClipAnimationConfig.AnimationScope scope, AnimationMode mode,
                         float duration, float delay, String easing, Transform transform) {
        static State capture(ClipAnimationConfig c) {
            return new State(c.getScope(), c.getMode(), c.getDuration(), c.getDelay(),
                    c.getEasing(), Transform.capture(c.getWholeContainer()));
        }
        void apply(TimelineModel model, TimelineClip clip) {
            if (model.findTrackForClip(clip) == null)
                throw new IllegalStateException("Animation clip is no longer in this timeline");
            var c = clip.getAnimationConfig();
            float oldDuration = c.getDuration();
            boolean retime = c.isWholeContainer() && scope == ClipAnimationConfig.AnimationScope.WHOLE_CONTAINER
                    && Float.compare(c.getDelay(), delay) == 0;
            c.setScope(scope); c.setMode(mode); c.setDuration(duration); c.setDelay(delay); c.setEasing(easing);
            transform.apply(c.getWholeContainer());
            if (retime) model.setCurrentTime(WholeContainerAnimation.retimePreviewTime(
                    clip.getStartTime(), clip.getEndTime(), delay, oldDuration, duration, model.getCurrentTime()));
        }
    }
    private final Consumer<TimelineClip> refreshSound;
    private TimelineModel frameModel, pendingModel;
    private TimelineClip frameClip, pendingClip;
    private ClipAnimationConfig pendingConfig;
    private State frameBefore, pendingBefore;
    private String pendingDescription;

    public WholeContainerAnimationEditSession(Consumer<TimelineClip> refreshSound) {
        this.refreshSound = refreshSound;
    }
    public void beginFrame(TimelineModel model, TimelineClip clip) {
        // A new project/world owns a different undo history; do not commit the old gesture there.
        if (pendingClip != null && pendingModel != model) clearPending();
        if (pendingClip != null && pendingClip != clip) finishIfIdle(false);
        if (pendingClip != null && pendingClip.getAnimationConfig() != pendingConfig) clearPending();
        frameModel = model;
        frameClip = clip;
        frameBefore = clip != null && clip.getAnimationConfig() != null
                ? State.capture(clip.getAnimationConfig()) : null;
    }
    public void endFrame(boolean modified, boolean itemActive, String description) {
        if (modified && frameBefore != null && frameModel != null
                && frameModel.findTrackForClip(frameClip) != null
                && (frameBefore.scope == ClipAnimationConfig.AnimationScope.WHOLE_CONTAINER
                    || frameClip.getAnimationConfig().isWholeContainer())) {
            if (pendingClip == null) {
                pendingClip = frameClip; pendingModel = frameModel;
                pendingConfig = frameClip.getAnimationConfig(); pendingBefore = frameBefore;
                pendingDescription = description;
            }
        }
        finishIfIdle(itemActive);
    }
    private void clearPending() {
        pendingClip = null; pendingModel = null; pendingConfig = null;
        pendingBefore = null; pendingDescription = null;
    }
    /** Also called when the animation section is collapsed on the release frame. */
    public void finishIfIdle(boolean itemActive) {
        if (itemActive || pendingClip == null) return;
        var clip = pendingClip;
        var model = pendingModel;
        var config = pendingConfig;
        var before = pendingBefore;
        var description = pendingDescription;
        clearPending();
        if (model.findTrackForClip(clip) == null || clip.getAnimationConfig() != config) return;
        var after = State.capture(config);
        if (before.equals(after)) return;
        TimelineCommandBridge.execute(new StateChangeCommand(model, description,
                () -> { before.apply(model, clip); refreshSound.accept(clip); },
                () -> { after.apply(model, clip); refreshSound.accept(clip); }));
    }
}

package com.director.ui.timeline.interaction;

import com.director.ui.timeline.commands.MoveAutomationKeyframeBetweenTracksCommand;
import com.director.ui.timeline.commands.MoveAutomationKeyframeCommand;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import com.director.ui.timeline.interaction.TimelineDragHandles.AutomationKeyframeHandle;
import com.director.ui.timeline.interaction.TimelineDragHandles.KeyframeHandle;
import com.director.ui.timeline.interaction.TimelineInteractionManager.InteractionMode;
import com.director.timeline.model.TimelineClip;
import com.director.ui.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineSelectable;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.ui.timeline.model.camera.CameraKeyframe;
import com.director.ui.timeline.render.TimelineRenderer;
import com.director.ui.timeline.snapping.TimelineSnapping;
import imgui.ImGui;
import imgui.ImGuiIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 关键帧（片段内与轨道级自动化）的点击与拖动逻辑。
 */
public final class TimelineKeyframeDragHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineKeyframeDragHandler.class);

    public interface Callbacks {
        void onDataChanged();

        void onKeyframeDoubleClick(TimelineClip clip, float mouseX, float contentX);
    }

    public interface SelectionSync {
        void syncPropertiesSelection(Object target);
    }

    private final TimelineModel model;
    private final TimelineInteractionManager interactionManager;
    private final TimelineCommandHistory commandHistory;
    private final TimelineRenderer renderer;
    private final TimelineSnapGuideState snapGuideState;
    private final Callbacks callbacks;
    private final SelectionSync selectionSync;

    public TimelineKeyframeDragHandler(
            TimelineModel model,
            TimelineInteractionManager interactionManager,
            TimelineCommandHistory commandHistory,
            TimelineRenderer renderer,
            TimelineSnapGuideState snapGuideState,
            Callbacks callbacks,
            SelectionSync selectionSync) {
        this.model = model;
        this.interactionManager = interactionManager;
        this.commandHistory = commandHistory;
        this.renderer = renderer;
        this.snapGuideState = snapGuideState;
        this.callbacks = callbacks;
        this.selectionSync = selectionSync;
    }

    public float getDraggedClipKeyframeTime(TimelineClip clip, float keyframeTime) {
        if (clip == null || interactionManager.getCurrentMode() != InteractionMode.DRAG_KEYFRAME) {
            return -1f;
        }
        Object active = interactionManager.getActiveObject();
        if (active instanceof KeyframeHandle handle && handle.clip == clip) {
            float originalTime = interactionManager.getDragStartValue();
            if (Math.abs(originalTime - keyframeTime) < 0.001f) {
                return handle.currentTime;
            }
        }
        return -1f;
    }

    public float getDraggedAutomationKeyframeTime(TimelineSelectable keyframe) {
        if (keyframe == null) {
            return -1f;
        }

        if (keyframe instanceof com.director.ui.timeline.model.audio.AudioEvent audioEvent) {
            float preview = com.director.ui.timeline.audio.TimelineAudioService.getInstance()
                    .getPreviewTimeForEvent(audioEvent);
            if (preview >= 0f) {
                return preview;
            }
        }

        if (interactionManager.getCurrentMode() == InteractionMode.DRAG_AUTOMATION_KEYFRAME) {
            Object active = interactionManager.getActiveObject();
            if (active instanceof AutomationKeyframeHandle handle) {
                if (handle.keyframe == keyframe || handle.keyframe.getId().equals(keyframe.getId())) {
                    return handle.currentTime;
                }
            }
        }
        return -1f;
    }

    public void onKeyframeClicked(TimelineHitResult hit, float mouseX, float contentX, boolean isDoubleClick) {
        TimelineTrack track = hit.track();
        TimelineClip clip = hit.clip();
        if (track == null || clip == null) {
            return;
        }

        if (track.isLocked()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 轨道已锁定，禁止操作关键帧 (轨道: {})", track.getName());
            }
            return;
        }

        if (isDoubleClick) {
            callbacks.onKeyframeDoubleClick(clip, mouseX, contentX);
            return;
        }

        float keyTime = hit.time();
        final float eps = 0.001f;
        var kf = clip.findKeyframeAt(keyTime, eps);
        if (kf == null) {
            LOGGER.warn("[Timeline] onKeyframeClicked: 未找到关键帧对象 @ {}s", keyTime);
            return;
        }

        ImGuiIO io = ImGui.getIO();
        model.selectItem(kf, io.getKeyCtrl());
        selectionSync.syncPropertiesSelection(kf);

        KeyframeHandle handle = new KeyframeHandle(track, clip, kf, kf.getTime());
        interactionManager.startInteraction(
                InteractionMode.DRAG_KEYFRAME,
                handle,
                mouseX, ImGui.getMousePosY(),
                kf.getTime(), 0f);
    }

    public void onAutomationKeyframeClicked(TimelineHitResult hit, float mouseX, float contentX, boolean isDoubleClick) {
        TimelineTrack track = hit.track();
        TimelineSelectable keyframe = hit.automationKeyframe();
        if (track == null || keyframe == null) {
            return;
        }

        if (track.isLocked()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 轨道已锁定，禁止操作自动化关键帧 (轨道: {})", track.getName());
            }
            return;
        }

        if (isDoubleClick) {
            LOGGER.debug("[Timeline] 双击自动化关键帧，但当前未实现删除功能");
            return;
        }

        if (!isActiveTrackForKeyframe(track)) {
            return;
        }

        ImGuiIO io = ImGui.getIO();
        boolean addToSelection = io.getKeyCtrl();
        model.selectItem(keyframe, addToSelection);
        selectionSync.syncPropertiesSelection(keyframe);

        if (track.getType() == TrackType.AUDIO && keyframe instanceof com.director.ui.timeline.model.audio.AudioEvent) {
            callbacks.onDataChanged();
            return;
        }

        if (keyframe instanceof CameraKeyframe cameraKeyframe) {
            com.director.timeline.playback.camera.TimelineCameraController.getInstance()
                    .previewAt(cameraKeyframe.getTime());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 预览摄像机关键帧 @ {:.2f}s", cameraKeyframe.getTime());
            }
        }

        AutomationKeyframeHandle handle = new AutomationKeyframeHandle(track, keyframe);
        interactionManager.startInteraction(
                InteractionMode.DRAG_AUTOMATION_KEYFRAME,
                handle,
                mouseX, ImGui.getMousePosY(),
                keyframe.getStartTime(), 0f);
        callbacks.onDataChanged();
    }

    public void dragKeyframe(float mouseX, float contentX) {
        Object active = interactionManager.getActiveObject();
        if (!(active instanceof KeyframeHandle handle)) {
            return;
        }

        if (handle.track != null && handle.track.isLocked()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 轨道已锁定，禁止拖动关键帧 (轨道: {})", handle.track.getName());
            }
            snapGuideState.clear();
            return;
        }

        TimelineSnapping.Result snap = TimelineSnapping.snapTime(
                model, renderer.xToTime(mouseX, contentX), handle.track, handle.clip, 0f);
        snapGuideState.update(snap);

        float eps = 0.001f;
        float newTime = Math.max(handle.clip.getStartTime() + eps,
                Math.min(handle.clip.getEndTime() - eps, snap.snappedTime));

        if (Math.abs(newTime - handle.currentTime) < eps) {
            return;
        }

        handle.currentTime = newTime;
        callbacks.onDataChanged();
    }

    public void dragAutomationKeyframe(float mouseX, float contentX, float mouseY, float trackAreaTop, float scrollY) {
        Object active = interactionManager.getActiveObject();
        if (!(active instanceof AutomationKeyframeHandle handle)) {
            return;
        }

        if (handle.currentTrack != null) {
            TrackType trackType = handle.currentTrack.getType();
            if (trackType == TrackType.CONTAINER) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Timeline] 容器轨道不支持轨道级关键帧拖动 (轨道: {})", handle.currentTrack.getName());
                }
                snapGuideState.clear();
                return;
            }
        }

        if (handle.currentTrack != null && handle.currentTrack.isLocked()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 轨道已锁定，禁止拖动自动化关键帧 (轨道: {})", handle.currentTrack.getName());
            }
            snapGuideState.clear();
            return;
        }

        boolean isAudioEvent = handle.currentTrack != null
                && handle.currentTrack.getType() == TrackType.AUDIO
                && handle.keyframe instanceof com.director.ui.timeline.model.audio.AudioEvent;
        if (isAudioEvent) {
            if (mouseY > 0 && trackAreaTop > 0) {
                int newTrackIndex = renderer.yToTrackIndex(mouseY - trackAreaTop, scrollY);
                if (newTrackIndex >= 0 && newTrackIndex < model.getTrackCount()) {
                    TimelineTrack newTrack = model.getTrackAt(newTrackIndex);
                    if (newTrack != null
                            && newTrack != handle.currentTrack
                            && !newTrack.isLocked()
                            && newTrack.getType() == TrackType.AUDIO) {
                        handle.currentTrack = newTrack;
                    }
                }
            }
            handle.currentTime = handle.keyframe.getStartTime();
            callbacks.onDataChanged();
            return;
        }

        if (!isActiveTrackForKeyframe(handle.currentTrack)) {
            snapGuideState.clear();
            return;
        }

        TimelineSnapping.Result snap = TimelineSnapping.snapTime(
                model, renderer.xToTime(mouseX, contentX), handle.currentTrack, null, 0f);
        snapGuideState.update(snap);
        float newTime = Math.max(0f, Math.min(model.getDuration(), snap.snappedTime));

        if (handle.keyframe instanceof CameraKeyframe cameraKeyframe && handle.currentTrack != null) {
            var customPathClip = handle.currentTrack.findCustomPathClipForKeyframe(cameraKeyframe);
            if (customPathClip != null) {
                float eps = 0.001f;
                newTime = Math.max(customPathClip.getStartTime() + eps,
                        Math.min(customPathClip.getEndTime() - eps, newTime));
            }
        }

        if (Math.abs(newTime - handle.currentTime) < 0.0005f) {
            return;
        }

        handle.currentTime = newTime;

        if (handle.keyframe instanceof CameraKeyframe) {
            com.director.timeline.playback.camera.TimelineCameraController.getInstance().previewAt(newTime);
        }
        callbacks.onDataChanged();
    }

    public boolean finishInteraction(InteractionMode mode, Object obj) {
        if (mode == InteractionMode.DRAG_KEYFRAME && obj instanceof KeyframeHandle handle) {
            float originalTime = interactionManager.getDragStartValue();
            float finalTime = handle.currentTime;
            if (Math.abs(finalTime - originalTime) > 0.001f) {
                commandHistory.executeCommand(
                        new com.director.ui.timeline.commands.MoveClipKeyframeCommand(
                                model, handle.track, handle.clip, handle.keyframe, originalTime, finalTime));
            }
            callbacks.onDataChanged();
            LOGGER.debug("[Timeline] 关键帧拖动完成 {} -> {}", originalTime, finalTime);
            return true;
        }

        if (mode == InteractionMode.DRAG_AUTOMATION_KEYFRAME && obj instanceof AutomationKeyframeHandle handle) {
            float originalTime = interactionManager.getDragStartValue();
            float finalTime = handle.currentTime;
            boolean trackChanged = handle.currentTrack != handle.originalTrack;

            if (handle.keyframe instanceof com.director.ui.timeline.model.audio.AudioEvent && trackChanged) {
                commandHistory.executeCommand(
                        new MoveAutomationKeyframeBetweenTracksCommand(
                                model, handle.originalTrack, handle.currentTrack, handle.keyframe, originalTime, finalTime));
            } else if (handle.keyframe instanceof CameraKeyframe cameraKeyframe
                    && handle.currentTrack != null
                    && handle.currentTrack.findCustomPathClipForKeyframe(cameraKeyframe) != null
                    && Math.abs(finalTime - originalTime) > 0.001f) {
                commandHistory.executeCommand(
                        new com.director.ui.timeline.commands.MoveCameraPathKeyframeCommand(
                                model,
                                handle.currentTrack.findCustomPathClipForKeyframe(cameraKeyframe),
                                cameraKeyframe,
                                originalTime,
                                finalTime));
            } else if (Math.abs(finalTime - originalTime) > 0.001f) {
                commandHistory.executeCommand(
                        new MoveAutomationKeyframeCommand(
                                model, handle.currentTrack, handle.keyframe, originalTime, finalTime));
            }

            callbacks.onDataChanged();
            LOGGER.debug("[Timeline] 自动化关键帧拖动完成 {} -> {}", originalTime, finalTime);
            return true;
        }

        return false;
    }

    private boolean isActiveTrackForKeyframe(TimelineTrack track) {
        if (track == null) {
            return true;
        }
        if (track.getType() == TrackType.CAMERA) {
            var active = com.director.timeline.playback.camera.TimelineCameraController.getInstance()
                    .getActiveCameraTrack();
            if (active != track) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Timeline] 摄像机轨道未激活，禁止操作关键帧 (轨道: {})", track.getName());
                }
                return false;
            }
        }
        if (track.getType() == TrackType.TIME) {
            var active = com.director.timeline.playback.time.TimelineWorldController.getInstance()
                    .getActiveTimeTrack();
            if (active != track) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Timeline] 时间轨道未激活，禁止操作关键帧 (轨道: {})", track.getName());
                }
                return false;
            }
        }
        if (track.getType() == TrackType.SEASON) {
            var active = com.director.timeline.playback.season.TimelineSeasonController.getInstance()
                    .getActiveSeasonTrack();
            if (active != track) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Timeline] 季节轨道未激活，禁止操作关键帧 (轨道: {})", track.getName());
                }
                return false;
            }
        }
        return true;
    }
}

package com.director.ui.timeline.interaction;

import com.director.ui.timeline.commands.MoveMarkerCommand;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import com.director.ui.timeline.interaction.TimelineDragHandles.MarkerHandle;
import com.director.ui.timeline.interaction.TimelineInteractionManager.InteractionMode;
import com.director.ui.timeline.model.TimelineMarker;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.playback.TimelineClipBlockController;
import com.director.timeline.playback.camera.TimelineCameraController;
import com.director.timeline.playback.season.TimelineSeasonController;
import com.director.timeline.playback.weather.TimelineWeatherController;
import com.director.ui.timeline.render.TimelineRenderer;
import com.director.ui.timeline.snapping.TimelineSnapping;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 播放头与时间标尺标记的交互逻辑。
 */
public final class TimelinePlayheadMarkerController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelinePlayheadMarkerController.class);

    public interface SnapGuide {
        void update(TimelineSnapping.Result snap);

        void clear();
    }

    public interface DataChangeNotifier {
        void onDataChanged();
    }

    private final TimelineModel model;
    private final TimelineInteractionManager interactionManager;
    private final TimelineRenderer renderer;
    private final TimelineCommandHistory commandHistory;
    private final SnapGuide snapGuide;
    private final DataChangeNotifier dataChangeNotifier;

    public TimelinePlayheadMarkerController(
            TimelineModel model,
            TimelineInteractionManager interactionManager,
            TimelineRenderer renderer,
            TimelineCommandHistory commandHistory,
            SnapGuide snapGuide,
            DataChangeNotifier dataChangeNotifier) {
        this.model = model;
        this.interactionManager = interactionManager;
        this.renderer = renderer;
        this.commandHistory = commandHistory;
        this.snapGuide = snapGuide;
        this.dataChangeNotifier = dataChangeNotifier;
    }

    public float getDraggedMarkerTime(TimelineMarker marker) {
        if (marker == null) {
            return -1f;
        }

        if (interactionManager.getCurrentMode() == InteractionMode.DRAG_MARKER) {
            Object active = interactionManager.getActiveObject();
            if (active instanceof MarkerHandle handle) {
                if (handle.marker == marker || handle.marker.getId().equals(marker.getId())) {
                    return handle.currentTime;
                }
            }
        }

        return -1f;
    }

    public void startPlayheadDrag(float mouseX, float contentX) {
        float rawTime = renderer.xToTime(mouseX, contentX);
        TimelineSnapping.Result snap = TimelineSnapping.snapTime(model, rawTime, null, null, 0f);
        snapGuide.update(snap);
        float clampedTime = Math.max(0f, Math.min(model.getDuration(), snap.snappedTime));
        model.setCurrentTime(clampedTime);
        model.setScrubbing(true);

        TimelineClipBlockController.getInstance().update();

        interactionManager.startInteraction(
                InteractionMode.DRAG_PLAYHEAD,
                null,
                mouseX, ImGui.getMousePosY(),
                clampedTime, 0f);
        TimelineCameraController.getInstance().beginScrubbing(clampedTime);

        dataChangeNotifier.onDataChanged();
        LOGGER.debug("[TimelinePlayheadMarkerController] 开始拖动播放头 @ {:.2f}s", clampedTime);
    }

    public void clickTimeRuler(float mouseX, float contentX) {
        float rawTime = renderer.xToTime(mouseX, contentX);
        TimelineSnapping.Result snap = TimelineSnapping.snapTime(model, rawTime, null, null, 0f);
        float clampedTime = Math.max(0f, Math.min(model.getDuration(), snap.snappedTime));

        model.setCurrentTime(clampedTime);
        TimelineSeasonController.getInstance().onTimelineTick(clampedTime);
        TimelineWeatherController.getInstance().previewAt(clampedTime);
        TimelineCameraController.getInstance().jumpToTimeAsPlayer(clampedTime);
        TimelineClipBlockController.getInstance().update();

        dataChangeNotifier.onDataChanged();
        LOGGER.debug("[TimelinePlayheadMarkerController] 点击时间标尺，跳转到 {:.2f}s", clampedTime);
    }

    public void startMarkerDrag(TimelineMarker marker, float mouseX, float contentX) {
        if (marker == null) {
            LOGGER.warn("[Timeline] 开始拖动标记失败：marker 为 null");
            return;
        }

        MarkerHandle handle = new MarkerHandle(marker);
        interactionManager.startInteraction(
                InteractionMode.DRAG_MARKER,
                handle,
                mouseX, ImGui.getMousePosY(),
                marker.getTime(), 0f);

        dataChangeNotifier.onDataChanged();
        LOGGER.debug("[TimelinePlayheadMarkerController] 开始拖动标记 @ {:.2f}s", marker.getTime());
    }

    public void updateMarkerDrag(float mouseX, float contentX) {
        if (!interactionManager.isInteracting(InteractionMode.DRAG_MARKER)) {
            return;
        }

        Object active = interactionManager.getActiveObject();
        if (!(active instanceof MarkerHandle handle)) {
            return;
        }

        float newTime = snapTime(mouseX, contentX);
        if (Math.abs(newTime - handle.currentTime) < 0.001f) {
            return;
        }

        handle.currentTime = newTime;
        dataChangeNotifier.onDataChanged();
    }

    public void endMarkerDrag() {
        if (!interactionManager.isInteracting(InteractionMode.DRAG_MARKER)) {
            return;
        }

        Object active = interactionManager.getActiveObject();
        if (active instanceof MarkerHandle handle) {
            float originalTime = interactionManager.getDragStartValue();
            float finalTime = handle.currentTime;

            if (Math.abs(finalTime - originalTime) > 0.001f) {
                commandHistory.executeCommand(
                        new MoveMarkerCommand(model, handle.marker, originalTime, finalTime));
            }

            dataChangeNotifier.onDataChanged();
            LOGGER.debug("[Timeline] 标记拖动完成 {} -> {}", originalTime, finalTime);
        }

        snapGuide.clear();
        interactionManager.endInteraction();
    }

    public boolean isDraggingMarker() {
        return interactionManager.isInteracting(InteractionMode.DRAG_MARKER);
    }

    public void dragMarker(float mouseX, float contentX) {
        Object active = interactionManager.getActiveObject();
        if (!(active instanceof MarkerHandle handle)) {
            return;
        }

        float newTime = snapTime(mouseX, contentX);
        if (Math.abs(newTime - handle.currentTime) < 0.001f) {
            return;
        }

        handle.currentTime = newTime;
        dataChangeNotifier.onDataChanged();
    }

    public void dragPlayhead(float mouseX, float contentX) {
        float clampedTime = snapTime(mouseX, contentX);
        model.setCurrentTime(clampedTime);
        TimelineSeasonController.getInstance().onTimelineTick(clampedTime);
        TimelineWeatherController.getInstance().previewAt(clampedTime);
        TimelineCameraController.getInstance().updateScrubbing(clampedTime);
        TimelineClipBlockController.getInstance().update();
        dataChangeNotifier.onDataChanged();
    }

    public void finishPlayheadDrag() {
        float finalTime = model.getCurrentTime();
        model.setScrubbing(false);
        TimelineCameraController.getInstance().endScrubbing(finalTime);
        LOGGER.debug("[TimelinePlayheadMarkerController] 拖动播放头结束 @ {:.2f}s", finalTime);
    }

    public void finishMarkerDragOnRelease(MarkerHandle handle) {
        float originalTime = interactionManager.getDragStartValue();
        float finalTime = handle.currentTime;

        if (Math.abs(finalTime - originalTime) > 0.001f) {
            commandHistory.executeCommand(
                    new MoveMarkerCommand(model, handle.marker, originalTime, finalTime));
        }

        dataChangeNotifier.onDataChanged();
        LOGGER.debug("[Timeline] 标记拖动完成 {} -> {}", originalTime, finalTime);
    }

    private float snapTime(float mouseX, float contentX) {
        float rawTime = renderer.xToTime(mouseX, contentX);
        TimelineSnapping.Result snap = TimelineSnapping.snapTime(model, rawTime, null, null, 0f);
        snapGuide.update(snap);
        return Math.max(0f, Math.min(model.getDuration(), snap.snappedTime));
    }
}

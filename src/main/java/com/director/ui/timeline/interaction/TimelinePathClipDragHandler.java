package com.director.ui.timeline.interaction;

import com.director.ui.timeline.commands.TimelineCommandHistory;
import com.director.ui.timeline.interaction.TimelineDragHandles.PathClipHandle;
import com.director.ui.timeline.interaction.TimelineInteractionManager.InteractionMode;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.TrackType;
import com.director.ui.timeline.model.camera.path.CameraPathClip;
import com.director.ui.timeline.render.TimelineRenderer;
import com.director.ui.timeline.snapping.TimelineSnapping;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiMouseCursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 摄像机路径片段（CameraPathClip）的点击、拖动与缩放逻辑。
 */
public final class TimelinePathClipDragHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelinePathClipDragHandler.class);

    public interface Callbacks {
        void onDataChanged();
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

    public TimelinePathClipDragHandler(
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

    public float getDraggedPathClipStartTime(CameraPathClip clip) {
        return getDraggedPathClipTime(clip, true);
    }

    public float getDraggedPathClipEndTime(CameraPathClip clip) {
        return getDraggedPathClipTime(clip, false);
    }

    private float getDraggedPathClipTime(CameraPathClip clip, boolean start) {
        if (clip == null) {
            return -1f;
        }
        InteractionMode mode = interactionManager.getCurrentMode();
        if (mode != InteractionMode.DRAG_PATH_CLIP
                && mode != InteractionMode.RESIZE_PATH_CLIP_LEFT
                && mode != InteractionMode.RESIZE_PATH_CLIP_RIGHT) {
            return -1f;
        }
        Object active = interactionManager.getActiveObject();
        if (active instanceof PathClipHandle handle
                && (handle.clip == clip || handle.clip.getId().equals(clip.getId()))) {
            return start ? handle.tempStart : handle.tempEnd;
        }
        return -1f;
    }

    public void onPathClipClicked(TimelineHitResult hit, float mouseX, float contentX, boolean isDoubleClick) {
        PathClipHit pathClipHit = hit.pathClipHit();
        if (pathClipHit == null) {
            return;
        }

        TimelineTrack track = pathClipHit.track();
        CameraPathClip clip = pathClipHit.clip();
        if (track == null || clip == null) {
            return;
        }

        if (track.isLocked()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 轨道已锁定，禁止操作 Path Clip (轨道: {})", track.getName());
            }
            return;
        }

        if (track.getType() == TrackType.CAMERA) {
            var activeTrack = com.director.ui.timeline.playback.camera.TimelineCameraController.getInstance()
                    .getActiveCameraTrack();
            if (activeTrack != track) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Timeline] 摄像机轨道未激活，禁止操作 Path Clip (轨道: {})", track.getName());
                }
                return;
            }
        }

        if (isDoubleClick) {
            model.selectItem(clip, false);
            selectionSync.syncPropertiesSelection(clip);
            callbacks.onDataChanged();
            return;
        }

        ImGuiIO io = ImGui.getIO();
        model.selectItem(clip, io.getKeyCtrl());
        selectionSync.syncPropertiesSelection(clip);

        PathClipHit.HitZone zone = pathClipHit.zone();
        PathClipHandle handle = new PathClipHandle(track, clip);

        InteractionMode mode = switch (zone) {
            case RESIZE_L -> InteractionMode.RESIZE_PATH_CLIP_LEFT;
            case RESIZE_R -> InteractionMode.RESIZE_PATH_CLIP_RIGHT;
            default -> InteractionMode.DRAG_PATH_CLIP;
        };

        interactionManager.startInteraction(
                mode, handle, mouseX, ImGui.getMousePosY(), clip.getStartTime(), clip.getEndTime());

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Timeline] 开始 {} Path Clip: {} (时间: {:.2f}s - {:.2f}s)",
                    mode, clip.getName(), clip.getStartTime(), clip.getEndTime());
        }
        callbacks.onDataChanged();
    }

    public void updateResizeCursor(TimelineHitResult hit, float contentX, float mouseX) {
        if (hit == null || hit.type() != TimelineHitResult.HitType.PATH_CLIP) {
            return;
        }

        PathClipHit pathClipHit = hit.pathClipHit();
        if (pathClipHit == null) {
            return;
        }

        CameraPathClip clip = pathClipHit.clip();
        TimelineTrack track = pathClipHit.track();
        if (clip == null || track == null || track.isLocked()) {
            return;
        }

        if (track.getType() == TrackType.CAMERA) {
            var activeTrack = com.director.ui.timeline.playback.camera.TimelineCameraController.getInstance()
                    .getActiveCameraTrack();
            if (activeTrack != track) {
                return;
            }
        }

        float startX = renderer.timeToX(clip.getStartTime(), contentX);
        float endX = renderer.timeToX(clip.getEndTime(), contentX);
        boolean nearLeft = Math.abs(mouseX - startX) <= TimelineInteractionConstants.CLIP_EDGE_RESIZE_PIXEL;
        boolean nearRight = Math.abs(mouseX - endX) <= TimelineInteractionConstants.CLIP_EDGE_RESIZE_PIXEL;
        float clipWidth = endX - startX;
        if (clipWidth < TimelineInteractionConstants.CLIP_EDGE_RESIZE_PIXEL * 3) {
            nearLeft = mouseX <= startX + TimelineInteractionConstants.CLIP_EDGE_RESIZE_PIXEL * 1.5f;
            nearRight = mouseX >= endX - TimelineInteractionConstants.CLIP_EDGE_RESIZE_PIXEL * 1.5f;
        }
        if (nearLeft || nearRight) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
        }
    }

    public void dragPathClip(float mouseX, float contentX) {
        Object active = interactionManager.getActiveObject();
        if (!(active instanceof PathClipHandle handle)) {
            return;
        }

        if (handle.track != null && handle.track.isLocked()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 轨道已锁定，禁止拖动 Path Clip (轨道: {})", handle.track.getName());
            }
            snapGuideState.clear();
            return;
        }

        float originalStart = interactionManager.getDragStartValue();
        float originalEnd = interactionManager.getDragStartValue2();
        float length = originalEnd - originalStart;

        float delta = renderer.xToTime(mouseX, contentX)
                - renderer.xToTime(interactionManager.getDragStartMouseX(), contentX);
        float candidateStart = Math.max(0f, originalStart + delta);

        TimelineSnapping.Result snap = TimelineSnapping.snapTime(
                model, candidateStart, handle.track, null, 0f);
        float snappedStart = Math.max(0f, snap.snappedTime);
        float snappedEnd = snappedStart + length;

        if (hasPathClipOverlap(handle.track, snappedStart, snappedEnd, handle.clip)) {
            return;
        }
        snapGuideState.update(snap);

        handle.tempStart = snappedStart;
        handle.tempEnd = snappedEnd;
        callbacks.onDataChanged();
    }

    public void resizePathClipLeft(float mouseX, float contentX) {
        resizePathClipEdge(mouseX, contentX, true);
    }

    public void resizePathClipRight(float mouseX, float contentX) {
        resizePathClipEdge(mouseX, contentX, false);
    }

    private void resizePathClipEdge(float mouseX, float contentX, boolean leftEdge) {
        Object active = interactionManager.getActiveObject();
        if (!(active instanceof PathClipHandle handle)) {
            return;
        }

        if (handle.track != null && handle.track.isLocked()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 轨道已锁定，禁止调整 Path Clip (轨道: {})", handle.track.getName());
            }
            snapGuideState.clear();
            return;
        }

        float originalStart = interactionManager.getDragStartValue();
        float originalEnd = interactionManager.getDragStartValue2();
        float minLength = TimelineInteractionConstants.MIN_PATH_CLIP_LENGTH;
        float delta = renderer.xToTime(mouseX, contentX)
                - renderer.xToTime(interactionManager.getDragStartMouseX(), contentX);

        if (leftEdge) {
            float candidateStart = Math.max(0f, Math.min(originalStart + delta, originalEnd - minLength));
            TimelineSnapping.Result snap = TimelineSnapping.snapTime(
                    model, candidateStart, handle.track, null, 0f);
            float snappedStart = Math.max(0f, Math.min(snap.snappedTime, originalEnd - minLength));
            if (hasPathClipOverlap(handle.track, snappedStart, originalEnd, handle.clip)) {
                return;
            }
            snapGuideState.update(snap);
            handle.tempStart = snappedStart;
            handle.tempEnd = originalEnd;
            handle.clip.setStartTime(snappedStart);
            handle.clip.setEndTime(originalEnd);
        } else {
            float candidateEnd = Math.max(originalStart + minLength, originalEnd + delta);
            TimelineSnapping.Result snap = TimelineSnapping.snapTime(
                    model, candidateEnd, handle.track, null, 0f);
            float snappedEnd = Math.max(snap.snappedTime, originalStart + minLength);
            if (hasPathClipOverlap(handle.track, originalStart, snappedEnd, handle.clip)) {
                return;
            }
            snapGuideState.update(snap);
            handle.tempStart = originalStart;
            handle.tempEnd = snappedEnd;
            handle.clip.setStartTime(originalStart);
            handle.clip.setEndTime(snappedEnd);
        }

        model.markDurationDirty();
        callbacks.onDataChanged();
    }

    public boolean finishInteraction(InteractionMode mode, Object obj) {
        if (!(obj instanceof PathClipHandle handle)) {
            return false;
        }

        float originalStart = interactionManager.getDragStartValue();
        float originalEnd = interactionManager.getDragStartValue2();
        float finalStart = handle.tempStart;
        float finalEnd = handle.tempEnd;

        return switch (mode) {
            case DRAG_PATH_CLIP -> {
                if (Math.abs(finalStart - originalStart) > 0.001f || Math.abs(finalEnd - originalEnd) > 0.001f) {
                    commandHistory.executeCommand(
                            new com.director.ui.timeline.commands.MovePathClipCommand(
                                    model, handle.track, handle.clip,
                                    originalStart, originalEnd, finalStart, finalEnd));
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[Timeline] Path Clip 拖动完成: {}s-{}s -> {}s-{}s",
                                originalStart, originalEnd, finalStart, finalEnd);
                    }
                }
                callbacks.onDataChanged();
                yield true;
            }
            case RESIZE_PATH_CLIP_LEFT -> {
                if (Math.abs(finalStart - originalStart) > 0.001f) {
                    commandHistory.executeCommand(
                            new com.director.ui.timeline.commands.ResizePathClipCommand(
                                    model, handle.track, handle.clip,
                                    originalStart, originalEnd, finalStart, finalEnd));
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[Timeline] Path Clip 左边缘调整完成: {}s -> {}s", originalStart, finalStart);
                    }
                }
                callbacks.onDataChanged();
                yield true;
            }
            case RESIZE_PATH_CLIP_RIGHT -> {
                if (Math.abs(finalEnd - originalEnd) > 0.001f) {
                    commandHistory.executeCommand(
                            new com.director.ui.timeline.commands.ResizePathClipCommand(
                                    model, handle.track, handle.clip,
                                    originalStart, originalEnd, finalStart, finalEnd));
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[Timeline] Path Clip 右边缘调整完成: {}s -> {}s", originalEnd, finalEnd);
                    }
                }
                callbacks.onDataChanged();
                yield true;
            }
            default -> false;
        };
    }

    public void cancelSilently(PathClipHandle handle) {
        handle.clip.setStartTime(interactionManager.getDragStartValue());
        handle.clip.setEndTime(interactionManager.getDragStartValue2());
        handle.tempStart = handle.clip.getStartTime();
        handle.tempEnd = handle.clip.getEndTime();
        model.markDurationDirty();
    }

    private static boolean hasPathClipOverlap(TimelineTrack track, float start, float end, CameraPathClip exclude) {
        return track != null && track.hasCameraPathOverlap(start, end, exclude);
    }
}

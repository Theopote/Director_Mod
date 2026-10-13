package com.director.ui.timeline.interaction;

import com.director.ui.timeline.commands.ResizeClipCommand;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import com.director.ui.timeline.interaction.TimelineDragHandles.ClipHandle;
import com.director.ui.timeline.interaction.TimelineInteractionManager.InteractionMode;
import com.director.timeline.layout.TimelineClipLayoutResolver;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.ui.timeline.render.TimelineRenderer;
import com.director.timeline.snapping.TimelineSnapping;
import imgui.ImGui;
import imgui.flag.ImGuiMouseCursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 容器片段（TimelineClip）的点击、拖动与缩放逻辑。
 */
public final class TimelineClipDragHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineClipDragHandler.class);

    public interface Callbacks {
        void onDataChanged();

        void onClipInteractionFinished(TimelineClip clip, float originalStartTime, float originalEndTime);

        void onKeyframeDoubleClick(TimelineClip clip, float mouseX, float contentX);
    }

    public interface SelectionSync {
        void syncClipSelection(TimelineClip clip);
    }

    private final TimelineModel model;
    private final TimelineInteractionManager interactionManager;
    private final TimelineCommandHistory commandHistory;
    private final TimelineRenderer renderer;
    private final TimelineSnapGuideState snapGuideState;
    private final Callbacks callbacks;
    private final SelectionSync selectionSync;

    public TimelineClipDragHandler(
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

    public void onClipClicked(TimelineHitResult hit, float mouseX, float contentX, boolean isDoubleClick) {
        TimelineTrack track = hit.track();
        TimelineClip clip = hit.clip();
        if (track == null || clip == null) {
            return;
        }

        if (track.isLocked()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 轨道已锁定，禁止操作片段: {} (轨道: {})",
                        clip.getLabel(), track.getName());
            }
            return;
        }

        model.setSelectedClip(clip);
        selectionSync.syncClipSelection(clip);
        callbacks.onDataChanged();

        boolean isAudioTrack = track.getType() == TrackType.AUDIO;
        if (isAudioTrack && isDoubleClick) {
            callbacks.onKeyframeDoubleClick(clip, mouseX, contentX);
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Timeline] 点击片段: {} (时间: {:.2f}s - {:.2f}s)",
                    clip.getLabel(), clip.getStartTime(), clip.getEndTime());
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

        if (isAudioTrack) {
            nearLeft = false;
            nearRight = false;
        }

        var audioService = com.director.ui.timeline.audio.TimelineAudioService.getInstance();
        audioService.beginClipTimeRangePreview(clip, clip.getStartTime(), clip.getEndTime());

        if (nearLeft) {
            interactionManager.startInteraction(
                    InteractionMode.RESIZE_CLIP_LEFT,
                    new ClipHandle(track, clip),
                    mouseX, ImGui.getMousePosY(),
                    clip.getStartTime(), clip.getEndTime());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 开始调整片段左边缘: {}", clip.getLabel());
            }
        } else if (nearRight) {
            interactionManager.startInteraction(
                    InteractionMode.RESIZE_CLIP_RIGHT,
                    new ClipHandle(track, clip),
                    mouseX, ImGui.getMousePosY(),
                    clip.getStartTime(), clip.getEndTime());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 开始调整片段右边缘: {}", clip.getLabel());
            }
        } else {
            interactionManager.startInteraction(
                    InteractionMode.DRAG_CLIP,
                    new ClipHandle(track, clip),
                    mouseX, ImGui.getMousePosY(),
                    clip.getStartTime(), clip.getEndTime());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 开始拖动片段: {} (@{:.2f}s)", clip.getLabel(), clip.getStartTime());
            }
        }
    }

    public void updateResizeCursor(TimelineHitResult hit, float contentX, float mouseX) {
        if (hit == null || hit.type() != TimelineHitResult.HitType.CLIP) {
            return;
        }

        TimelineClip clip = hit.clip();
        if (clip == null) {
            return;
        }

        TimelineTrack track = hit.track();
        if (track != null) {
            if (track.getType() == TrackType.AUDIO || track.isLocked()) {
                return;
            }
        }

        if (isNearClipEdge(clip, contentX, mouseX)) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
        }
    }

    public void dragClip(float mouseX, float contentX, float mouseY, float trackAreaTop) {
        Object active = interactionManager.getActiveObject();
        if (!(active instanceof ClipHandle handle)) {
            return;
        }

        TimelineTrack currentTrack = model.findTrackForClip(handle.clip);
        if (currentTrack == null) {
            currentTrack = handle.track;
        }
        if (currentTrack != null && currentTrack.isLocked()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 轨道已锁定，禁止拖动片段 (轨道: {})", currentTrack.getName());
            }
            snapGuideState.clear();
            return;
        }

        if (model.getSelectedClip() != handle.clip) {
            model.setSelectedClip(handle.clip);
        }

        float start0 = interactionManager.getDragStartValue();
        float end0 = interactionManager.getDragStartValue2();
        float length = end0 - start0;

        float startMouseX = interactionManager.getDragStartMouseX();
        float delta = renderer.xToTime(mouseX, contentX) - renderer.xToTime(startMouseX, contentX);

        boolean isBoundAudioClip = handle.clip instanceof com.director.timeline.model.audio.AudioTimelineClip
                && com.director.ui.timeline.audio.TimelineAudioService.getInstance().isAudioClipBound(handle.clip);
        if (isBoundAudioClip) {
            delta = 0f;
        }

        float candidateStart = Math.max(0f, start0 + delta);
        TimelineTrack targetTrack = currentTrack;

        if (mouseY > 0 && trackAreaTop > 0) {
            float scrollY = model.getViewportState().getScrollY();
            int newTrackIndex = renderer.yToTrackIndex(mouseY - trackAreaTop, scrollY);
            if (newTrackIndex >= 0 && newTrackIndex < model.getTrackCount()) {
                TimelineTrack newTrack = model.getTrackAt(newTrackIndex);
                if (newTrack != null && newTrack != currentTrack && !newTrack.isLocked()) {
                    if (!isClipAllowedOnTrack(handle.clip, newTrack)) {
                        newTrack = null;
                    }
                    if (newTrack == null) {
                        targetTrack = currentTrack;
                    } else {
                        if (currentTrack != null) {
                            currentTrack.removeClip(handle.clip);
                        }
                        if (!newTrack.getClips().contains(handle.clip)) {
                            newTrack.addClip(handle.clip);
                        }
                        targetTrack = newTrack;
                        if (newTrack.getType() == TrackType.AUDIO) {
                            com.director.ui.timeline.audio.TimelineAudioService.getInstance()
                                    .moveLinkedEventsToTrack(handle.clip, newTrack);
                        }
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("[Timeline] 片段跨轨道移动: {} -> {} (片段: {})",
                                    currentTrack != null ? currentTrack.getName() : "null",
                                    newTrack.getName(), handle.clip.getLabel());
                        }
                    }
                }
            }
        }

        float snappedStart;
        if (isBoundAudioClip) {
            snapGuideState.clear();
            snappedStart = Math.max(0f, start0);
        } else {
            TimelineSnapping.Result snap = TimelineSnapping.snapTime(
                    model, candidateStart, targetTrack, handle.clip, 0f);
            snapGuideState.update(snap);
            snappedStart = Math.max(0f, snap.snappedTime);
        }

        if (targetTrack != null && !isBoundAudioClip) {
            snappedStart = targetTrack.resolveClipStartTime(snappedStart, length, handle.clip);
        }

        handle.clip.setTimeRange(snappedStart, snappedStart + length);
        model.markDurationDirty();

        com.director.ui.timeline.audio.TimelineAudioService.getInstance()
                .updateClipTimeRangePreview(handle.clip, handle.clip.getStartTime(), handle.clip.getEndTime());
        callbacks.onDataChanged();
    }

    public void resizeClipLeft(float mouseX, float contentX) {
        resizeClipEdge(mouseX, contentX, true);
    }

    public void resizeClipRight(float mouseX, float contentX) {
        resizeClipEdge(mouseX, contentX, false);
    }

    private void resizeClipEdge(float mouseX, float contentX, boolean leftEdge) {
        Object active = interactionManager.getActiveObject();
        if (!(active instanceof ClipHandle handle)) {
            return;
        }

        if (handle.track != null && handle.track.isLocked()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Timeline] 轨道已锁定，禁止调整片段宽度 (轨道: {})", handle.track.getName());
            }
            snapGuideState.clear();
            return;
        }

        if (model.getSelectedClip() != handle.clip) {
            model.setSelectedClip(handle.clip);
        }

        float originalStart = interactionManager.getDragStartValue();
        float originalEnd = interactionManager.getDragStartValue2();
        float minLength = TimelineInteractionConstants.MIN_CLIP_LENGTH;

        float delta = renderer.xToTime(mouseX, contentX)
                - renderer.xToTime(interactionManager.getDragStartMouseX(), contentX);

        if (leftEdge) {
            float candidateStart = Math.max(0f, Math.min(originalStart + delta, originalEnd - minLength));
            TimelineSnapping.Result snap = TimelineSnapping.snapTime(
                    model, candidateStart, handle.track, handle.clip, 0f);
            snapGuideState.update(snap);
            float snappedStart = Math.max(0f, Math.min(snap.snappedTime, handle.clip.getEndTime() - minLength));
            if (handle.track != null) {
                var occupied = TimelineClipLayoutResolver.clipIntervalsOnTrack(handle.track, handle.clip);
                snappedStart = TimelineClipLayoutResolver.clampResizeStart(
                        occupied, snappedStart, originalEnd, minLength, 0f);
            }
            handle.clip.setStartTime(snappedStart);
        } else {
            float candidateEnd = Math.max(originalStart + minLength, originalEnd + delta);
            TimelineSnapping.Result snap = TimelineSnapping.snapTime(
                    model, candidateEnd, handle.track, handle.clip, 0f);
            snapGuideState.update(snap);
            float snappedEnd = Math.max(snap.snappedTime, handle.clip.getStartTime() + minLength);
            if (handle.track != null) {
                var occupied = TimelineClipLayoutResolver.clipIntervalsOnTrack(handle.track, handle.clip);
                snappedEnd = TimelineClipLayoutResolver.clampResizeEnd(
                        occupied, originalStart, snappedEnd, minLength);
            }
            handle.clip.setEndTime(snappedEnd);
        }

        model.markDurationDirty();
        com.director.ui.timeline.audio.TimelineAudioService.getInstance()
                .updateClipTimeRangePreview(handle.clip, handle.clip.getStartTime(), handle.clip.getEndTime());
        callbacks.onDataChanged();
    }

    public boolean finishInteraction(InteractionMode mode, Object obj) {
        if (!(obj instanceof ClipHandle handle)) {
            return false;
        }

        return switch (mode) {
            case DRAG_CLIP -> {
                float oldStart = interactionManager.getDragStartValue();
                float oldEnd = interactionManager.getDragStartValue2();
                var target = model.findTrackForClip(handle.clip);
                if (target != handle.track || Math.abs(oldStart - handle.clip.getStartTime()) > 0.001f
                        || Math.abs(oldEnd - handle.clip.getEndTime()) > 0.001f) {
                    commandHistory.executeCommand(new com.director.ui.timeline.commands.MoveClipCommand(
                            model, handle.clip, handle.track, target, oldStart, oldEnd,
                            handle.clip.getStartTime(), handle.clip.getEndTime()));
                    callbacks.onDataChanged();
                }
                com.director.ui.timeline.audio.TimelineAudioService.getInstance()
                        .endClipTimeRangePreview(handle.clip);
                yield true;
            }
            case RESIZE_CLIP_LEFT -> {
                finishResize(handle, true);
                yield true;
            }
            case RESIZE_CLIP_RIGHT -> {
                finishResize(handle, false);
                yield true;
            }
            default -> false;
        };
    }

    public void cancelSilently(ClipHandle handle) {
        TimelineTrack currentTrack = model.findTrackForClip(handle.clip);
        if (currentTrack != null && currentTrack != handle.track) {
            currentTrack.removeClip(handle.clip);
            if (handle.track != null && !handle.track.getClips().contains(handle.clip)) {
                handle.track.addClip(handle.clip);
            }
        }
        com.director.ui.timeline.audio.TimelineAudioService.getInstance().moveLinkedEventsToTrack(handle.clip, handle.track);
        handle.clip.setTimeRange(
                interactionManager.getDragStartValue(),
                interactionManager.getDragStartValue2());
        model.markDurationDirty();
    }

    private void finishResize(ClipHandle handle, boolean leftEdge) {
        float originalStart = interactionManager.getDragStartValue();
        float originalEnd = interactionManager.getDragStartValue2();
        float currentStart = handle.clip.getStartTime();
        float currentEnd = handle.clip.getEndTime();

        boolean changed = leftEdge
                ? Math.abs(currentStart - originalStart) > 0.001f
                : Math.abs(currentEnd - originalEnd) > 0.001f;

        if (changed) {
            commandHistory.executeCommand(
                    new ResizeClipCommand(model, handle.clip, originalStart, originalEnd, currentStart, currentEnd));
            if (LOGGER.isDebugEnabled()) {
                if (leftEdge) {
                    LOGGER.debug("[Timeline] 调整片段左边缘: {}s -> {}s (片段: {})",
                            originalStart, currentStart, handle.clip.getLabel());
                } else {
                    LOGGER.debug("[Timeline] 调整片段右边缘: {}s -> {}s (片段: {})",
                            originalEnd, currentEnd, handle.clip.getLabel());
                }
            }
        }

        com.director.ui.timeline.audio.TimelineAudioService.getInstance()
                .endClipTimeRangePreview(handle.clip);
        callbacks.onDataChanged();
    }

    private boolean isNearClipEdge(TimelineClip clip, float contentX, float mouseX) {
        float startX = renderer.timeToX(clip.getStartTime(), contentX);
        float endX = renderer.timeToX(clip.getEndTime(), contentX);
        boolean nearLeft = Math.abs(mouseX - startX) <= TimelineInteractionConstants.CLIP_EDGE_RESIZE_PIXEL;
        boolean nearRight = Math.abs(mouseX - endX) <= TimelineInteractionConstants.CLIP_EDGE_RESIZE_PIXEL;
        float clipWidth = endX - startX;
        if (clipWidth < TimelineInteractionConstants.CLIP_EDGE_RESIZE_PIXEL * 3) {
            nearLeft = mouseX <= startX + TimelineInteractionConstants.CLIP_EDGE_RESIZE_PIXEL * 1.5f;
            nearRight = mouseX >= endX - TimelineInteractionConstants.CLIP_EDGE_RESIZE_PIXEL * 1.5f;
        }
        return nearLeft || nearRight;
    }

    static boolean isClipAllowedOnTrack(TimelineClip clip, TimelineTrack track) {
        if (clip == null || track == null) {
            return false;
        }
        TrackType tt = track.getType();
        if (clip instanceof com.director.timeline.model.entity.EntityTimelineClip entity) {
            if (tt != TrackType.ACTOR || track.getActorTrackPayload() == null) return false;
            // Payload ownership is not transferred by this generic clip drag handler.
            // Keep instance clips on their owning actor track; reuse assets via Attach instead.
            if (!track.getActorTrackPayload().getClips().contains(entity.getEntityClip())) return false;
        }
        if (tt == null) {
            return true;
        }
        if (clip instanceof com.director.timeline.model.audio.AudioTimelineClip) return tt == TrackType.AUDIO;
        if (clip.getBoundContainerId() == null) {
            return true;
        }
        if (clip instanceof com.director.timeline.model.audio.AudioTimelineClip) {
            return tt == TrackType.AUDIO;
        }
        return tt == TrackType.CONTAINER;
    }
}

package com.director.ui.timeline.interaction;

import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineSelectable;
import com.director.timeline.model.TimelineTrack;
import com.director.ui.timeline.render.TimelineColors;
import com.director.ui.timeline.render.TimelineRenderer;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiMouseButton;

import java.util.ArrayList;
import java.util.List;

/**
 * 时间轴框选（Marquee Select）逻辑。
 */
public final class TimelineMarqueeSelector {

    private static final float ACTIVATION_THRESHOLD_PX = 4.0f;

    public interface SelectionSync {
        void onSelectionCleared();

        void onMarqueeSelectionApplied(List<Object> selected, Object lastSelected);
    }

    private final TimelineModel model;
    private final TimelineRenderer renderer;
    private final TimelineInteractionManager interactionManager;
    private final SelectionSync selectionSync;

    private boolean candidate;
    private boolean active;
    private float startX;
    private float startY;
    private float curX;
    private float curY;

    public TimelineMarqueeSelector(
            TimelineModel model,
            TimelineRenderer renderer,
            TimelineInteractionManager interactionManager,
            SelectionSync selectionSync) {
        this.model = model;
        this.renderer = renderer;
        this.interactionManager = interactionManager;
        this.selectionSync = selectionSync;
    }

    public void startCandidate(float mouseX, float mouseY) {
        if (interactionManager.isInteracting()) {
            return;
        }
        candidate = true;
        active = false;
        startX = mouseX;
        startY = mouseY;
        curX = mouseX;
        curY = mouseY;
    }

    public void update(float contentX, float trackAreaTop, float scrollY) {
        if (!candidate) {
            return;
        }

        if (ImGui.isMouseDown(ImGuiMouseButton.Left)) {
            curX = ImGui.getMousePosX();
            curY = ImGui.getMousePosY();

            if (!active) {
                float dx = curX - startX;
                float dy = curY - startY;
                if ((dx * dx + dy * dy) >= (ACTIVATION_THRESHOLD_PX * ACTIVATION_THRESHOLD_PX)) {
                    active = true;
                }
            }
            return;
        }

        if (!active) {
            model.clearSelection();
            selectionSync.onSelectionCleared();
        } else {
            applySelection(contentX, trackAreaTop, scrollY);
        }

        candidate = false;
        active = false;
    }

    public void renderOverlay(ImDrawList drawList) {
        if (!active) {
            return;
        }

        float x1 = Math.min(startX, curX);
        float y1 = Math.min(startY, curY);
        float x2 = Math.max(startX, curX);
        float y2 = Math.max(startY, curY);

        int fill = TimelineColors.rgba(80, 160, 255, 35);
        int border = TimelineColors.rgba(80, 160, 255, 180);
        drawList.addRectFilled(x1, y1, x2, y2, fill, 2.0f);
        drawList.addRect(x1, y1, x2, y2, border, 2.0f, 0, 1.5f);
    }

    private void applySelection(float contentX, float trackAreaTop, float scrollY) {
        float x1 = Math.min(startX, curX);
        float y1 = Math.min(startY, curY);
        float x2 = Math.max(startX, curX);
        float y2 = Math.max(startY, curY);

        ImGuiIO io = ImGui.getIO();
        boolean additive = io.getKeyCtrl();

        float pps = model.getPixelsPerSecond();
        float scrollX = model.getScrollX();
        float rowHeight = TimelineRenderer.getTrackHeight() + TimelineRenderer.getTrackGap();
        float r = TimelineRenderer.getKeyframeHitRadius();

        int startIdx = renderer.yToTrackIndex(y1 - trackAreaTop, scrollY);
        int endIdx = renderer.yToTrackIndex(y2 - trackAreaTop, scrollY);
        if (startIdx < 0) {
            startIdx = 0;
        }
        if (endIdx < 0) {
            endIdx = 0;
        }
        int maxIdx = model.getTrackCount() - 1;
        startIdx = Math.max(0, Math.min(maxIdx, startIdx));
        endIdx = Math.max(0, Math.min(maxIdx, endIdx));
        if (endIdx < startIdx) {
            int tmp = startIdx;
            startIdx = endIdx;
            endIdx = tmp;
        }

        List<Object> selected = new ArrayList<>();

        for (int idx = startIdx; idx <= endIdx; idx++) {
            TimelineTrack track = model.getTrackAt(idx);
            if (track == null || !track.isVisible()) {
                continue;
            }

            float trackTop = trackAreaTop + renderer.getMath().trackContentY(idx) - scrollY;
            float centerY = trackTop + (TimelineRenderer.getTrackHeight() / 2.0f);
            if (centerY < (y1 - r) || centerY > (y2 + r)) {
                continue;
            }

            List<TimelineClip> clips = track.getClips();
            if (clips != null) {
                for (TimelineClip clip : clips) {
                    if (clip == null) {
                        continue;
                    }
                    List<com.director.timeline.model.clip.ClipKeyframe> kfs = clip.getClipKeyframes();
                    if (kfs == null || kfs.isEmpty()) {
                        continue;
                    }
                    for (com.director.timeline.model.clip.ClipKeyframe kf : kfs) {
                        if (kf == null) {
                            continue;
                        }
                        float kx = contentX + (kf.getTime() * pps) - scrollX;
                        if (kx >= (x1 - r) && kx <= (x2 + r)) {
                            selected.add(kf);
                        }
                    }
                }
            }

            if (track.hasAutomationKeyframes()) {
                for (TimelineSelectable kf : track.getAutomationKeyframes()) {
                    if (kf == null) {
                        continue;
                    }
                    float kx = contentX + (kf.getStartTime() * pps) - scrollX;
                    if (kx >= (x1 - r) && kx <= (x2 + r)) {
                        selected.add(kf);
                    }
                }
            }
        }

        model.addItemsToSelection(selected, !additive);
        if (!selected.isEmpty()) {
            selectionSync.onMarqueeSelectionApplied(selected, selected.getLast());
        }
    }
}

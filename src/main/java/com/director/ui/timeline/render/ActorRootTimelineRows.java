package com.director.ui.timeline.render;

import com.director.timeline.model.*;
import com.director.timeline.model.entity.*;
import com.director.core.DirectorServices;
import com.director.ui.actor.selection.ActorSelectionHub;
import imgui.*;
import imgui.flag.ImGuiMouseButton;
import java.util.*;

/** Root TRS rows are a view of one shared key, not three independent keys. */
final class ActorRootTimelineRows {
    private com.director.ui.actor.pose.ActorRootKeyframeTimeSession drag;
    private float dragMouseX, previewTime;
    private float dragPixelsPerSecond;
    boolean isDragging() { return drag != null || marquee != null; }
    void cancel() { if (drag != null) drag.cancel(); drag = null; marquee = null; }
    boolean updateDrag(TimelineModel model) {
        if (drag == null) return false;
        if (drag.model() != model || !drag.isCurrent() || ImGui.isKeyPressed(imgui.flag.ImGuiKey.Escape)
                || com.director.ui.actor.gizmo.ActorGizmoController.getInstance().isDragging()
                || com.director.ui.actor.pose.ActorBoneGizmoController.getInstance().isDragging()) { cancel(); return true; }
        float delta = (ImGui.getMousePosX() - dragMouseX) / Math.max(1, dragPixelsPerSecond);
        float target = drag.originalTime() + delta;
        if (model.isSnapToGridEnabled() && !ImGui.getIO().getKeyAlt()) target = model.snapToGrid(target);
        previewTime = Math.max(drag.start(), Math.min(drag.end(), target));
        ImGui.setTooltip(net.minecraft.client.resource.language.I18n.translate("gui.director.actor.root_rows.drag", previewTime));
        if (ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            var session = drag; drag = null;
            if (Math.abs(ImGui.getMousePosX() - dragMouseX) >= 3
                    && ImGui.isWindowHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenBlockedByActiveItem)) {
                if (session.commit(previewTime)) model.setCurrentTime(previewTime);
            } else session.cancel();
        } else if (!ImGui.isMouseDown(ImGuiMouseButton.Left)) cancel();
        return true;
    }
    private record Marquee(TimelineModel model, TimelineTrack owner, EntityClip clip, long revision,
            float x, float y, float left, float right, float top, float bottom, float rowsTop,
            boolean additive, List<Float> viewport, Map<EntityClipKeyframe, Float> times) {}
    private Marquee marquee;
    private static List<Float> viewport(float left, float right, float top, float height, float scroll, TimelineRenderMath math) {
        return List.of(left, right, top, height, scroll, math.getPixelsPerSecond(), math.timeToX(0), math.getRowLayout().totalHeight());
    }
    static Set<EntityClipKeyframe> rectangle(Map<EntityClipKeyframe, Float> times, float x1, float y1, float x2, float y2,
                                             float rowsTop, TimelineRenderMath math) {
        Set<EntityClipKeyframe> result = new LinkedHashSet<>();
        for (var entry : times.entrySet()) for (int row = 0; row < COUNT; row++) {
            float x = math.timeToX(entry.getValue()), y = rowsTop + (row + .5f) * ActorBoneTimelineRows.CHILD_HEIGHT;
            if (x >= Math.min(x1, x2) && x <= Math.max(x1, x2) && y >= Math.min(y1, y2) && y <= Math.max(y1, y2)) result.add(entry.getKey());
        }
        return result;
    }
    void beginMarquee(TimelineModel model, TimelineTrack track, float mx, float my, float left, float right,
                      float top, float height, float scroll, float rowsTop, TimelineRenderMath math, boolean additive) {
        if (!(model.getSelectedClip() instanceof EntityTimelineClip wrapper) || wrapper.getParentTrack() != track
                || !clips(track).contains(wrapper.getEntityClip())) { ImGui.setTooltip(tr("marquee_hint")); return; }
        var clip = wrapper.getEntityClip();
        if (model.isPlaying() || model.isScrubbing()) return;
        var times = new LinkedHashMap<EntityClipKeyframe, Float>();
        clip.getKeyframes().forEach(k -> times.put(k, k.getTime()));
        boolean keep = additive && focusModel == model && focusClip == clip;
        focusModel = model; focusOwner = track; focusClip = clip;
        marquee = new Marquee(model, track, clip, model.getActorEditContextRevision(), mx, my, left, right,
                Math.max(top, rowsTop), Math.min(top + height, rowsTop + COUNT * ActorBoneTimelineRows.CHILD_HEIGHT), rowsTop,
                keep, viewport(left, right, top, height, scroll, math), Collections.unmodifiableMap(times));
    }
    boolean updateMarquee(TimelineModel model, float left, float right, float top, float height, float scroll, TimelineRenderMath math) {
        if (marquee == null) return false;
        var box = marquee;
        var times = new LinkedHashMap<EntityClipKeyframe, Float>(); box.clip().getKeyframes().forEach(k -> times.put(k, k.getTime()));
        if (box.model() != model || box.revision() != model.getActorEditContextRevision()
                || model.getSelectedClip() != box.clip().getTimelineClip() || !model.getAllTracks().contains(box.owner())
                || !clips(box.owner()).contains(box.clip()) || !times.equals(box.times())
                || !box.viewport().equals(viewport(left, right, top, height, scroll, math))
                || model.isPlaying() || model.isScrubbing()
                || com.director.ui.actor.gizmo.ActorGizmoController.getInstance().isDragging()
                || com.director.ui.actor.pose.ActorBoneGizmoController.getInstance().isDragging()
                || ImGui.isKeyPressed(imgui.flag.ImGuiKey.Escape)
                || !ImGui.isWindowHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenBlockedByActiveItem)) { marquee = null; return true; }
        float mx = Math.clamp(ImGui.getMousePosX(), box.left(), box.right());
        float my = Math.clamp(ImGui.getMousePosY(), box.top(), box.bottom());
        boolean active = Math.hypot(mx - box.x(), my - box.y()) >= 4;
        var inside = active ? rectangle(times, box.x(), box.y(), mx, my, box.rowsTop(), math) : Set.<EntityClipKeyframe>of();
        if (active) {
            var draw = ImGui.getWindowDrawList(); draw.pushClipRect(box.left(), box.top(), box.right(), box.bottom(), true);
            try {
                draw.addRectFilled(Math.min(mx, box.x()), Math.min(my, box.y()), Math.max(mx, box.x()), Math.max(my, box.y()), TimelineColors.rgba(80,160,230,40));
                draw.addRect(Math.min(mx, box.x()), Math.min(my, box.y()), Math.max(mx, box.x()), Math.max(my, box.y()), TimelineColors.rgba(100,190,255,220));
            } finally { draw.popClipRect(); }
        }
        ImGui.setTooltip(tr("marquee_hint"));
        if (ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            marquee = null;
            if (!box.additive()) selected.clear();
            selected.addAll(inside); focusKey = selected.isEmpty() ? null : selected.iterator().next();
        } else if (!ImGui.isMouseDown(ImGuiMouseButton.Left)) marquee = null;
        return true;
    }
    private final com.director.ui.actor.pose.ActorRootKeyframeClipboard clipboard = new com.director.ui.actor.pose.ActorRootKeyframeClipboard();
    private TimelineModel focusModel;
    private TimelineTrack focusOwner;
    private EntityClip focusClip;
    private EntityClipKeyframe focusKey;
    private final Set<EntityClipKeyframe> selected = new LinkedHashSet<>();
    void clearFocus() { selected.clear(); focusModel = null; focusOwner = null; focusClip = null; focusKey = null; }
    void focus(TimelineModel model, TimelineTrack owner, EntityClip clip, EntityClipKeyframe key) {
        clearFocus(); select(model, owner, clip, key, false);
    }
    void select(TimelineModel model, TimelineTrack owner, EntityClip clip, EntityClipKeyframe key, boolean additive) {
        if ((!additive && !selected.contains(key)) || focusModel != model || focusOwner != owner || focusClip != clip) selected.clear();
        focusModel = model; focusOwner = owner; focusClip = clip; focusKey = key;
        if (additive && selected.contains(key)) selected.remove(key); else selected.add(key);
    }
    boolean handleKeyCommand(TimelineModel model, String action) {
        if (focusModel != model || focusClip == null || model.getSelectedClip() != focusClip.getTimelineClip()
                || !model.getAllTracks().contains(focusOwner)) { clearFocus(); return false; }
        // Key focus must never fall through to operations on the actor reference.
        if (isDragging()) return true;
        boolean live = !selected.isEmpty() && focusClip.getKeyframes().containsAll(selected);
        if (action.equals("copy") && live) clipboard.copy(focusClip, selected);
        if (action.equals("cut") && live
                && com.director.ui.actor.pose.ActorRootKeyframeEditing.editable(model, focusOwner, focusClip)
                && clipboard.copy(focusClip, selected))
            com.director.ui.actor.pose.ActorRootKeyframeClipboard.delete(model, focusOwner, focusClip, selected);
        if (action.equals("delete") && live)
            com.director.ui.actor.pose.ActorRootKeyframeClipboard.delete(model, focusOwner, focusClip, selected);
        if (action.equals("paste")) {
            var pasted = clipboard.pasteAll(model, focusOwner, focusClip);
            if (!pasted.isEmpty()) { selected.clear(); selected.addAll(pasted); focusKey = pasted.getFirst(); }
        }
        return true;
    }
    static final int COUNT = 3;
    record Hit(EntityClip clip, EntityClipKeyframe key, float x) {}
    static List<EntityClip> clips(TimelineTrack track) {
        var payload = track.getActorTrackPayload();
        return payload == null ? List.of() : payload.getClips().stream()
                .filter(c -> c instanceof EntitySpawnClip || c instanceof EntityTransformClip).toList();
    }
    static boolean selectedAt(TimelineModel model, EntityClip clip, EntityClipKeyframe key) {
        return model.getSelectedClip() == clip.getTimelineClip() && Math.abs(key.getTime() - model.getCurrentTime()) <= 1e-4f;
    }
    private static String tr(String key) {
        return net.minecraft.client.resource.language.I18n.translate("gui.director.actor.root_rows." + key);
    }
    void render(ImDrawList draw, TimelineModel model, TimelineTrack track, float x, float left, float right,
                float top, TimelineRenderMath math) {
        float height = ActorBoneTimelineRows.CHILD_HEIGHT;
        String[] labels = {tr("position"), tr("rotation"), tr("scale")};
        int[] colors = {TimelineColors.rgba(100,190,255,255), TimelineColors.rgba(255,190,90,255), TimelineColors.rgba(140,220,130,255)};
        for (int row = 0; row < COUNT; row++) {
            float y = top + row * height;
            draw.addRectFilled(x, y, right, y + height, TimelineColors.rgba(29,32,37,255));
            draw.pushClipRect(x, y, left, y + height, true);
            draw.addText(x + 16, y + 4, TimelineColors.COLOR_TEXT_LIGHT, labels[row]); draw.popClipRect();
            draw.addLine(left, y, left, y + height, TimelineColors.COLOR_SEPARATOR_LINE);
            draw.addLine(x, y + height, right, y + height, TimelineColors.COLOR_SEPARATOR_LINE);
            draw.pushClipRect(left, y, right, y + height, true);
            try {
                for (var clip : clips(track)) for (var key : clip.getKeyframes()) {
                    float kx = math.timeToX(drag != null && drag.contains(key) && drag.clip() == clip ? drag.previewTime(key, previewTime) : key.getTime()), ky = y + height / 2;
                    if (kx < left - 6 || kx > right + 6) continue;
                    int color = clip.isMuted() || track.isMuted() ? TimelineColors.rgba(110,110,110,255)
                            : (focusModel == model && focusClip == clip && model.getSelectedClip() == clip.getTimelineClip() && selected.contains(key) || selectedAt(model,clip,key)) ? TimelineColors.rgba(255,255,255,255) : colors[row];
                    draw.addQuadFilled(kx,ky-5,kx+5,ky,kx,ky+5,kx-5,ky,color);
                    if (drag == null && ImGui.isWindowHovered() && Math.abs(ImGui.getMousePosX() - kx) <= 7
                            && Math.abs(ImGui.getMousePosY() - ky) <= 7) ImGui.setTooltip(tr("selection_hint"));
                }
            } finally { draw.popClipRect(); }
        }
    }
    boolean click(TimelineModel model, TimelineTrack track, float mouseX, float left, int row, TimelineRenderMath math) {
        if (row < 0 || row >= COUNT) return false;
        // Consume right clicks on root rows; they must not reach bone or clip menus.
        if (!ImGui.isMouseClicked(ImGuiMouseButton.Left)) return true;
        var preferred = model.getSelectedClip();
        List<Hit> hits = new ArrayList<>();
        if (mouseX >= left) for (var clip : clips(track)) for (var key : clip.getKeyframes()) {
            float x = math.timeToX(key.getTime());
            if (Math.abs(x - mouseX) <= 7) hits.add(new Hit(clip,key,x));
        }
        var preferredHits = hits.stream().filter(h -> h.clip().getTimelineClip() == preferred).toList();
        if (!preferredHits.isEmpty()) hits = new ArrayList<>(preferredHits);
        if (hits.size() > 1) { ImGui.setTooltip(tr("ambiguous")); return true; }
        ActorSelectionHub.selectActor(track.getActorTrackPayload().getActorRef().getActorId());
        com.director.ui.actor.pose.ActorBoneSelectionController.getInstance().deactivate();
        if (hits.isEmpty() && mouseX < left) clearFocus();
        if (hits.size() == 1) {
            var hit = hits.getFirst();
            model.setSelectedClip(hit.clip().getTimelineClip()); model.setCurrentTime(hit.key().getTime());
            DirectorServices.get().uiSelection().setCurrentSelection(hit.clip().getTimelineClip());
            select(model, track, hit.clip(), hit.key(), ImGui.getIO().getKeyCtrl());
            if (!ImGui.getIO().getKeyCtrl()
                    && com.director.ui.actor.pose.ActorRootKeyframeEditing.editable(model, track, hit.clip())) {
                try {
                    drag = new com.director.ui.actor.pose.ActorRootKeyframeTimeSession(model, track, hit.clip(), hit.key(), selected);
                    dragMouseX = mouseX; dragPixelsPerSecond = math.getPixelsPerSecond(); previewTime = hit.key().getTime();
                } catch (IllegalArgumentException unavailable) { cancel(); }
            }
        }
        return true;
    }
}

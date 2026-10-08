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
    private TimelineRenderMath dragMath;
    boolean isDragging() { return drag != null; }
    void cancel() { if (drag != null) drag.cancel(); drag = null; }
    boolean updateDrag(TimelineModel model) {
        if (drag == null) return false;
        if (drag.model() != model || !drag.isCurrent() || ImGui.isKeyPressed(imgui.flag.ImGuiKey.Escape)
                || com.director.ui.actor.gizmo.ActorGizmoController.getInstance().isDragging()
                || com.director.ui.actor.pose.ActorBoneGizmoController.getInstance().isDragging()) { cancel(); return true; }
        float delta = (ImGui.getMousePosX() - dragMouseX) / Math.max(1, dragMath.getPixelsPerSecond());
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
    private final com.director.ui.actor.pose.ActorRootKeyframeClipboard clipboard = new com.director.ui.actor.pose.ActorRootKeyframeClipboard();
    private TimelineModel focusModel;
    private TimelineTrack focusOwner;
    private EntityClip focusClip;
    private EntityClipKeyframe focusKey;
    private final Set<EntityClipKeyframe> selected = new LinkedHashSet<>();
    void clearFocus() { selected.clear(); focusModel = null; focusOwner = null; focusClip = null; focusKey = null; }
    void focus(TimelineModel model, TimelineTrack owner, EntityClip clip, EntityClipKeyframe key) {
        select(model, owner, clip, key, false);
    }
    void select(TimelineModel model, TimelineTrack owner, EntityClip clip, EntityClipKeyframe key, boolean additive) {
        if (!additive || focusModel != model || focusOwner != owner || focusClip != clip) selected.clear();
        focusModel = model; focusOwner = owner; focusClip = clip; focusKey = key;
        if (additive && selected.contains(key)) selected.remove(key); else selected.add(key);
    }
    boolean handleKeyCommand(TimelineModel model, String action) {
        if (focusModel != model || focusClip == null || model.getSelectedClip() != focusClip.getTimelineClip()
                || !model.getAllTracks().contains(focusOwner)) { clearFocus(); return false; }
        // Key focus must never fall through to operations on the actor reference.
        if (drag != null) return true;
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
                    float kx = math.timeToX(drag != null && drag.key() == key && drag.clip() == clip ? previewTime : key.getTime()), ky = y + height / 2;
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
        if (hits.isEmpty()) clearFocus();
        if (hits.size() == 1) {
            var hit = hits.getFirst();
            model.setSelectedClip(hit.clip().getTimelineClip()); model.setCurrentTime(hit.key().getTime());
            DirectorServices.get().uiSelection().setCurrentSelection(hit.clip().getTimelineClip());
            select(model, track, hit.clip(), hit.key(), ImGui.getIO().getKeyCtrl());
            if (!ImGui.getIO().getKeyCtrl()
                    && com.director.ui.actor.pose.ActorRootKeyframeEditing.editable(model, track, hit.clip())) {
                try {
                    drag = new com.director.ui.actor.pose.ActorRootKeyframeTimeSession(model, track, hit.clip(), hit.key());
                    dragMouseX = mouseX; dragMath = math; previewTime = hit.key().getTime();
                } catch (IllegalArgumentException unavailable) { cancel(); }
            }
        }
        return true;
    }
}

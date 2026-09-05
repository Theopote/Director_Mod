package com.director.ui.timeline.render;

import com.director.actor.ActorRegistryManager;
import com.director.actor.rig.HumanoidRigProfile;
import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.ui.actor.pose.ActorBoneSelectionController;
import com.director.ui.actor.selection.ActorSelectionHub;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import java.util.*;

/** View-only expansion state, scoped to one timeline renderer. */
public final class ActorBoneTimelineRows {
    public static final float CHILD_HEIGHT = 24f;
    private final Set<TimelineTrack> expanded = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<TimelineTrack> supported = Collections.newSetFromMap(new IdentityHashMap<>());

    public TimelineRowLayout prepare(TimelineModel model, float height, float gap) {
        var tracks = model.getAllTracks();
        expanded.retainAll(tracks);
        supported.clear();
        int[] counts = new int[model.getTrackCount()];
        for (int i = 0; i < counts.length; i++) {
            var track = model.getTrackAt(i);
            var payload = track.getActorTrackPayload();
            if (payload == null || payload.getActorRef() == null) continue;
            var registry = ActorRegistryManager.getInstance();
            var actor = registry.getActor(payload.getActorRef().getActorId()).orElse(null);
            if (actor == null || !(actor.getRigProfile() instanceof HumanoidRigProfile)) continue;
            supported.add(track);
            if (expanded.contains(track)) counts[i] = HumanoidRig.DEFINITION.boneCount();
        }
        return new TimelineRowLayout(height, gap, CHILD_HEIGHT, counts);
    }

    public boolean supports(TimelineTrack track) { return supported.contains(track); }

    public void render(ImDrawList draw, TimelineTrack track, float x, float contentX, float right,
                       float y, float height, float gap) {
        if (!supports(track)) return;
        draw.addText(x + 8, y + 6, TimelineColors.COLOR_TEXT_LIGHT, expanded.contains(track) ? "v" : ">");
        if (!expanded.contains(track)) return;
        var selection = ActorBoneSelectionController.getInstance();
        var actorId = track.getActorTrackPayload().getActorRef().getActorId();
        var rig = HumanoidRig.DEFINITION;
        for (int i = 0; i < rig.boneCount(); i++) {
            var bone = rig.bone(i);
            float top = y + height + gap + i * CHILD_HEIGHT;
            boolean selected = actorId.equals(selection.getActorId()) && selection.isBoneSelected(bone.id());
            draw.addRectFilled(x, top, right, top + CHILD_HEIGHT,
                    selected ? TimelineColors.rgba(48, 77, 100, 255) : TimelineColors.rgba(29, 32, 37, 255));
            int depth = 0;
            for (int parent = bone.parentIndex(); parent >= 0; parent = rig.bone(parent).parentIndex()) depth++;
            draw.pushClipRect(x, top, contentX, top + CHILD_HEIGHT, true);
            draw.addText(x + 16 + depth * 12, top + 4, TimelineColors.COLOR_TEXT_LIGHT, bone.displayName());
            draw.popClipRect();
            draw.addLine(contentX, top, contentX, top + CHILD_HEIGHT, TimelineColors.COLOR_SEPARATOR_LINE);
            draw.addLine(x, top + CHILD_HEIGHT, right, top + CHILD_HEIGHT, TimelineColors.COLOR_SEPARATOR_LINE);
        }
    }

    /** Consume only a new click; ongoing timeline drags must still receive updates/releases. */
    public boolean handleClick(TimelineModel model, TimelineRowLayout layout, float x, float contentX,
                               float right, float top, float viewportHeight, float scrollY, float height, float gap) {
        if (!ImGui.isWindowHovered() || (!ImGui.isMouseClicked(ImGuiMouseButton.Left)
                && !ImGui.isMouseClicked(ImGuiMouseButton.Right))) return false;
        float mx = ImGui.getMousePosX(), my = ImGui.getMousePosY();
        if (mx < x || mx >= right || my < top || my >= top + viewportHeight) return false;
        float contentY = my - top + scrollY;
        int owner = layout.ownerAt(contentY);
        if (owner < 0) return false;
        var track = model.getTrackAt(owner);
        if (!supports(track)) return false;
        float local = contentY - layout.trackTop(owner);
        if (local < height && mx < Math.min(contentX, x + 26)) {
            if (ImGui.isMouseClicked(ImGuiMouseButton.Left) && !expanded.remove(track)) expanded.add(track);
            return true;
        }
        if (!expanded.contains(track) || local < height + gap) return false;
        int bone = (int) ((local - height - gap) / CHILD_HEIGHT);
        if (bone >= HumanoidRig.DEFINITION.boneCount()) return false;
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            var actorId = track.getActorTrackPayload().getActorRef().getActorId();
            var selection = ActorBoneSelectionController.getInstance();
            if (!selection.isActive()) selection.activate(actorId);
            else selection.synchronizeActor(actorId);
            ActorSelectionHub.selectActor(actorId);
            model.selectItem(track, false);
            selection.selectBone(HumanoidRig.DEFINITION.bone(bone).id(), ImGui.getIO().getKeyCtrl());
        }
        return true;
    }
}

package com.director.ui.timeline.render;

import com.director.actor.ActorRegistryManager;
import com.director.actor.animation.asset.*;
import com.director.timeline.model.entity.EntityAnimationClip;
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
    private record KeyHit(TimelineTrack track, String bone, float x, float y, float time) {}
    private final List<KeyHit> keyHits = new ArrayList<>();

    public TimelineRowLayout prepare(TimelineModel model, float height, float gap) {
        keyHits.clear();
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
                       float y, float height, float gap, TimelineRenderMath math) {
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
            renderKeys(draw, track, bone.id(), contentX, right, top, math);
        }
    }

    private void renderKeys(ImDrawList draw, TimelineTrack track, String bone, float left, float right,
                            float top, TimelineRenderMath math) {
        draw.pushClipRect(left, top, right, top + CHILD_HEIGHT, true);
        try {
            for (var clip : track.getActorTrackPayload().getClips()) {
                if (!(clip instanceof EntityAnimationClip animation) || clip.isMuted()) continue;
                var asset = AnimationAssetLibrary.getInstance().find(animation.getAnimationId()).orElse(null);
                if (asset == null || !asset.rigId().equals(HumanoidRig.DEFINITION.id())) continue;
                var boneTrack = asset.findBoneTrack(bone).orElse(null);
                if (boneTrack == null) continue;
                var channels = List.of(boneTrack.translation(), boneTrack.rotation(), boneTrack.scale());
                int[] colors = {TimelineColors.rgba(100, 190, 255, 255),
                        TimelineColors.rgba(255, 190, 90, 255), TimelineColors.rgba(140, 220, 130, 255)};
                for (int channel = 0; channel < channels.size(); channel++) {
                    float cy = top + 5 + channel * 7;
                    Set<Integer> pixels = new HashSet<>();
                    for (var key : channels.get(channel).keyframes()) {
                        var mode = animation.isLoop() ? asset.loopMode() : AnimationLoopMode.ONCE;
                        for (float time : ActorKeyframeProjection.times(key.time(), asset.duration(), mode,
                                clip.getStartTime(), clip.getEndTime(), animation.getSpeed(),
                                math.xToTime(left), math.xToTime(right))) {
                            float px = math.timeToX(time);
                            if (!pixels.add(Math.round(px))) continue;
                            draw.addCircleFilled(px, cy, 3, colors[channel]);
                            keyHits.add(new KeyHit(track, bone, px, cy, time));
                            if (ImGui.isWindowHovered() && Math.abs(ImGui.getMousePosX() - px) < 4
                                    && Math.abs(ImGui.getMousePosY() - cy) < 4) {
                                ImGui.setTooltip(asset.name() + " · " + new String[]{"位置", "旋转", "缩放"}[channel]
                                        + String.format(" · %.3fs\n点击定位；编辑请使用骨骼详情区。", time));
                            }
                        }
                    }
                }
            }
        } finally { draw.popClipRect(); }
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
            if (mx >= contentX) {
                KeyHit closest = null;
                float distance = 36f;
                for (var hit : keyHits) {
                    if (hit.track() != track || !hit.bone().equals(HumanoidRig.DEFINITION.bone(bone).id())) continue;
                    float d = (mx - hit.x()) * (mx - hit.x()) + (my - hit.y()) * (my - hit.y());
                    if (d < distance) { closest = hit; distance = d; }
                }
                if (closest != null) model.setCurrentTime(closest.time());
            }
        }
        return true;
    }
}

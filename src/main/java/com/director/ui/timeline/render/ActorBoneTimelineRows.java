package com.director.ui.timeline.render;

import com.director.actor.ActorRegistryManager;
import com.director.actor.animation.asset.*;
import com.director.timeline.model.entity.EntityAnimationClip;
import com.director.actor.rig.HumanoidRigProfile;
import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.ui.actor.pose.ActorBoneSelectionController;
import com.director.ui.actor.pose.ActorAnimationKeyframeEditing;
import imgui.flag.ImGuiKey;
import com.director.ui.actor.selection.ActorSelectionHub;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiHoveredFlags;
import java.util.*;

/** View-only expansion state, scoped to one timeline renderer. */
public final class ActorBoneTimelineRows {
    public static final float CHILD_HEIGHT = 24f;
    private final Set<TimelineTrack> expanded = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<TimelineTrack> supported = Collections.newSetFromMap(new IdentityHashMap<>());
    private record KeyHit(TimelineTrack track, String bone, float x, float y, float time,
                          AnimationAsset asset, EntityAnimationClip clip, BoneTrack boneTrack, int channel, float assetTime) {}
    private final List<KeyHit> keyHits = new ArrayList<>();
    private KeyHit dragging;
    private float dragMouseX;
    private float dragPixelsPerSecond;
    private float dragStart, dragEnd, dragSpeed;
    private Object dragSource;

    private static Object source(KeyHit hit) {
        return switch (hit.channel()) {
            case 0 -> hit.boneTrack().translation().findAt(hit.assetTime()).orElse(null);
            case 1 -> hit.boneTrack().rotation().findAt(hit.assetTime()).orElse(null);
            default -> hit.boneTrack().scale().findAt(hit.assetTime()).orElse(null);
        };
    }

    /** Preview only while held; the underlying asset changes once, on release. */
    public boolean updateDrag(TimelineModel model) {
        if (dragging == null) return false;
        var hit = dragging;
        if (ImGui.isKeyPressed(ImGuiKey.Escape) || hit.track().isLocked()
                || hit.clip().getStartTime() != dragStart || hit.clip().getEndTime() != dragEnd
                || hit.clip().getSpeed() != dragSpeed || !Objects.equals(source(hit), dragSource)
                || (hit.clip().isLoop() && hit.asset().loopMode() != AnimationLoopMode.ONCE)
                || !model.getAllTracks().contains(hit.track())
                || !hit.track().getActorTrackPayload().getClips().contains(hit.clip())
                || AnimationAssetLibrary.getInstance().find(hit.asset().id()).orElse(null) != hit.asset()
                || !hit.clip().getAnimationId().equals(hit.asset().id()) || hit.clip().isMuted()) {
            dragging = null;
            return true;
        }
        float target = ActorKeyframeDrag.target(hit.assetTime(), ImGui.getMousePosX() - dragMouseX,
                dragPixelsPerSecond, hit.clip().getSpeed(), hit.asset().duration(),
                hit.clip().getEndTime() - hit.clip().getStartTime());
        ImGui.setTooltip(String.format("资产时间 %.3fs → %.3fs\n共享资产的所有引用都会变化；Esc 取消。", hit.assetTime(), target));
        if (ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            dragging = null;
            if (isCanvasWindowHovered() && Math.abs(ImGui.getMousePosX() - dragMouseX) >= 3) {
                float delta = target - hit.assetTime();
                boolean moved = switch (hit.channel()) {
                    case 0 -> ActorAnimationKeyframeEditing.moveTranslations(model,
                            List.of(new ActorAnimationKeyframeEditing.TranslationKey(hit.boneTrack(), hit.assetTime())), delta);
                    case 1 -> ActorAnimationKeyframeEditing.moveRotation(model, hit.boneTrack(), hit.assetTime(), target);
                    default -> ActorAnimationKeyframeEditing.moveScales(model,
                            List.of(new ActorAnimationKeyframeEditing.ScaleKey(hit.boneTrack(), hit.assetTime())), delta);
                };
                if (moved) model.setCurrentTime(hit.clip().getStartTime() + target / Math.max(0.01f, hit.clip().getSpeed()));
            }
        } else if (!ImGui.isMouseDown(ImGuiMouseButton.Left)) dragging = null;
        return true;
    }

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
            if (actor == null || com.director.actor.rig.ActorRigCompatibility.family(actor.getType())
                    != com.director.actor.rig.ActorRigCompatibility.Family.HUMANOID) continue;
            supported.add(track);
            if (expanded.contains(track)) counts[i] = HumanoidRig.DEFINITION.boneCount();
        }
        return new TimelineRowLayout(height, gap, CHILD_HEIGHT, counts);
    }

    public boolean supports(TimelineTrack track) { return supported.contains(track); }

    /** The timeline's invisible canvas button owns ActiveId on mouse-down.
     * Keep popup/window occlusion checks, but do not let that button block its own controls. */
    private static boolean isCanvasWindowHovered() {
        return ImGui.isWindowHovered(ImGuiHoveredFlags.AllowWhenBlockedByActiveItem);
    }

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
                            if (dragging != null && dragging.clip() == animation && dragging.boneTrack() == boneTrack
                                    && dragging.channel() == channel && dragging.assetTime() == key.time()) {
                                float target = ActorKeyframeDrag.target(key.time(), ImGui.getMousePosX() - dragMouseX,
                                        dragPixelsPerSecond, animation.getSpeed(), asset.duration(), clip.getEndTime() - clip.getStartTime());
                                float previewX = math.timeToX(clip.getStartTime() + target / Math.max(0.01f, animation.getSpeed()));
                                draw.addCircle(previewX, cy, 5, colors[channel]);
                            }
                            keyHits.add(new KeyHit(track, bone, px, cy, time, asset, animation, boneTrack, channel, key.time()));
                            if (isCanvasWindowHovered() && Math.abs(ImGui.getMousePosX() - px) < 4
                                    && Math.abs(ImGui.getMousePosY() - cy) < 4) {
                                ImGui.setTooltip(asset.name() + " · " + new String[]{"位置", "旋转", "缩放"}[channel]
                                        + String.format(" · %.3fs\n点击定位；非循环片段可拖动。共享资产影响全部引用。", time));
                            }
                        }
                    }
                }
            }
        } finally { draw.popClipRect(); }
    }

    /** Consume only a new click; ongoing timeline drags must still receive updates/releases. */
    public boolean handleClick(TimelineModel model, TimelineRowLayout layout, float x, float contentX,
                               float right, float top, float viewportHeight, float scrollY, float height, float gap,
                               float pixelsPerSecond) {
        if (!isCanvasWindowHovered() || (!ImGui.isMouseClicked(ImGuiMouseButton.Left)
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
                if (closest != null) {
                    model.setCurrentTime(closest.time());
                    if (!track.isLocked() && (!closest.clip().isLoop() || closest.asset().loopMode() == AnimationLoopMode.ONCE)) {
                        dragging = closest;
                        dragMouseX = mx;
                        dragPixelsPerSecond = pixelsPerSecond;
                        dragStart = closest.clip().getStartTime();
                        dragEnd = closest.clip().getEndTime();
                        dragSpeed = closest.clip().getSpeed();
                        dragSource = source(closest);
                    }
                }
            }
        }
        return true;
    }
}

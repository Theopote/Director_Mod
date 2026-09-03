package com.director.ui.actor.pose;

import com.director.actor.animation.asset.AnimationAsset;
import com.director.actor.animation.asset.AnimationAssetLibrary;
import com.director.actor.animation.asset.BoneTrack;
import com.director.actor.animation.asset.ChannelKeyframe;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.entity.ActorTrack;
import com.director.timeline.model.entity.EntityAnimationClip;
import com.director.timeline.model.entity.EntityClip;
import com.director.ui.theme.Icons;
import imgui.ImGui;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Compact dope sheet for the active rig animation on the selected actor. */
public final class ActorAnimationDopeSheet {
    private static String selectedAssetId;
    private static final Set<KeyId> selection = new LinkedHashSet<>();
    private static final List<CopiedKey> clipboard = new ArrayList<>();

    private ActorAnimationDopeSheet() {}

    public static void render(TimelineModel model, UUID actorId) {
        ActorTrack actorTrack = findActorTrack(model, actorId);
        EntityAnimationClip clip = findActiveClip(actorTrack, model == null ? 0f : model.getCurrentTime());
        AnimationAsset asset = clip == null ? null
                : AnimationAssetLibrary.getInstance().find(clip.getAnimationId()).orElse(null);
        if (model == null || asset == null) {
            ImGui.textDisabled("当前播放头没有可编辑的关键帧动画");
            return;
        }

        ImGui.text("资产: " + asset.name());
        ImGui.sameLine();
        ImGui.textDisabled(String.format("%.2fs", asset.duration()));
        if (asset.boneTracks().isEmpty()) {
            ImGui.textDisabled("旋转骨骼后将在这里显示关键帧");
            return;
        }

        for (Map.Entry<String, BoneTrack> entry : asset.boneTracks().entrySet()) {
            if (entry.getValue().rotation().isEmpty()) continue;
            String boneId = entry.getKey();
            if (ImGui.treeNode(boneId + "##dopeBone:" + asset.id())) {
                for (ChannelKeyframe<Quaternionf> frame : entry.getValue().rotation().keyframes()) {
                    KeyId key = new KeyId(boneId, frame.time());
                    boolean selected = Objects.equals(selectedAssetId, asset.id()) && selection.contains(key);
                    String label = String.format("%s %.3fs  [%s]##dope:%s:%s:%f",
                            Icons.KEYFRAME, frame.time(), frame.interpolation(), asset.id(), boneId, frame.time());
                    if (ImGui.selectable(label, selected)) {
                        select(asset.id(), key, ImGui.getIO().getKeyCtrl());
                        model.setCurrentTime(toTimelineTime(clip, frame.time()));
                    }
                }
                ImGui.treePop();
            }
        }

        pruneSelection(asset);
        List<ActorAnimationKeyframeEditing.RotationKey> selectedKeys = selectedKeys(asset);
        float localFrame = Math.max(0.01f, clip.getSpeed()) / Math.max(1f, model.getFrameRate());
        if (!selectedKeys.isEmpty()) {
            ImGui.textDisabled("已选择 " + selectedKeys.size() + " 个关键帧（Ctrl+点击多选）");
            if (ImGui.smallButton(Icons.ARROW_LEFT + " 1帧##dopePrevious")) {
                if (withinDuration(-localFrame, asset.duration())
                        && ActorAnimationKeyframeEditing.moveRotations(model, selectedKeys, -localFrame)) {
                    shiftSelection(-localFrame);
                    seekToFirst(model, clip);
                }
            }
            ImGui.sameLine();
            if (ImGui.smallButton(Icons.ARROW_RIGHT + " 1帧##dopeNext")) {
                if (withinDuration(localFrame, asset.duration())
                        && ActorAnimationKeyframeEditing.moveRotations(model, selectedKeys, localFrame)) {
                    shiftSelection(localFrame);
                    seekToFirst(model, clip);
                }
            }
            ImGui.sameLine();
            if (ImGui.smallButton(Icons.COPY + " 复制##dopeCopy")) copySelection(asset);
            ImGui.sameLine();
            if (ImGui.smallButton(Icons.DELETE + " 删除##dopeDelete")) {
                if (ActorAnimationKeyframeEditing.deleteRotations(model, selectedKeys)) clearSelection();
            }
        }
        if (!clipboard.isEmpty()) {
            if (ImGui.smallButton(Icons.CLIPBOARD + " 粘贴到播放头##dopePaste")) {
                float localTime = Math.max(0f, (model.getCurrentTime() - clip.getStartTime())
                        * Math.max(0.01f, clip.getSpeed()));
                pasteAt(model, asset, Math.min(asset.duration(), localTime));
            }
            ImGui.sameLine();
            ImGui.textDisabled(clipboard.size() + " 个");
        }
    }

    private static float toTimelineTime(EntityAnimationClip clip, float localTime) {
        return clip.getStartTime() + localTime / Math.max(0.01f, clip.getSpeed());
    }

    private static void select(String assetId, KeyId key, boolean additive) {
        if (!Objects.equals(selectedAssetId, assetId) || !additive) selection.clear();
        selectedAssetId = assetId;
        if (additive && selection.contains(key)) selection.remove(key); else selection.add(key);
    }

    private static void clearSelection() {
        selectedAssetId = null;
        selection.clear();
    }

    private static void pruneSelection(AnimationAsset asset) {
        if (!Objects.equals(selectedAssetId, asset.id())) return;
        selection.removeIf(key -> asset.findBoneTrack(key.boneId()).isEmpty()
                || asset.findBoneTrack(key.boneId()).orElseThrow().rotation().findAt(key.time()).isEmpty());
        if (selection.isEmpty()) selectedAssetId = null;
    }

    private static List<ActorAnimationKeyframeEditing.RotationKey> selectedKeys(AnimationAsset asset) {
        List<ActorAnimationKeyframeEditing.RotationKey> result = new ArrayList<>();
        if (!Objects.equals(selectedAssetId, asset.id())) return result;
        for (KeyId key : selection) asset.findBoneTrack(key.boneId()).ifPresent(
                track -> result.add(new ActorAnimationKeyframeEditing.RotationKey(track, key.time())));
        return result;
    }

    private static boolean withinDuration(float delta, float duration) {
        for (KeyId key : selection) {
            float target = key.time() + delta;
            if (target < 0f || target > duration) return false;
        }
        return true;
    }

    private static void shiftSelection(float delta) {
        Set<KeyId> shifted = new LinkedHashSet<>();
        for (KeyId key : selection) shifted.add(new KeyId(key.boneId(), key.time() + delta));
        selection.clear();
        selection.addAll(shifted);
    }

    private static void seekToFirst(TimelineModel model, EntityAnimationClip clip) {
        if (!selection.isEmpty()) model.setCurrentTime(toTimelineTime(clip, selection.iterator().next().time()));
    }

    private static void copySelection(AnimationAsset asset) {
        clipboard.clear();
        if (selection.isEmpty()) return;
        float origin = selection.stream().map(KeyId::time).min(Float::compare).orElse(0f);
        for (KeyId key : selection) asset.findBoneTrack(key.boneId()).flatMap(
                track -> track.rotation().findAt(key.time())).ifPresent(frame -> clipboard.add(
                        new CopiedKey(key.boneId(), frame.time() - origin, frame.value(), frame.interpolation())));
    }

    private static void pasteAt(TimelineModel model, AnimationAsset asset, float origin) {
        List<ActorAnimationKeyframeEditing.RotationPaste> pasted = new ArrayList<>();
        Set<KeyId> pastedIds = new LinkedHashSet<>();
        for (CopiedKey copied : clipboard) {
            float time = origin + copied.offset();
            if (time > asset.duration()) return;
            BoneTrack track;
            try {
                track = asset.getOrCreateBoneTrack(copied.boneId());
            } catch (IllegalArgumentException ignored) {
                return;
            }
            pasted.add(new ActorAnimationKeyframeEditing.RotationPaste(
                    track, time, copied.value(), copied.interpolation()));
            pastedIds.add(new KeyId(copied.boneId(), time));
        }
        if (ActorAnimationKeyframeEditing.pasteRotations(model, pasted)) {
            selectedAssetId = asset.id();
            selection.clear();
            selection.addAll(pastedIds);
        }
    }

    private record KeyId(String boneId, float time) {}
    private record CopiedKey(String boneId, float offset, Quaternionf value,
                             com.director.actor.animation.asset.InterpolationMode interpolation) {
        private CopiedKey {
            value = new Quaternionf(value);
        }
    }

    private static ActorTrack findActorTrack(TimelineModel model, UUID actorId) {
        if (model == null || actorId == null) return null;
        for (TimelineTrack track : model.getTracks()) {
            if (track == null || track.getType() != TrackType.ACTOR) continue;
            ActorTrack payload = track.getActorTrackPayload();
            if (payload != null && payload.getActorRef() != null
                    && Objects.equals(actorId, payload.getActorRef().getActorId())) return payload;
        }
        return null;
    }

    private static EntityAnimationClip findActiveClip(ActorTrack track, float time) {
        if (track == null) return null;
        EntityAnimationClip result = null;
        for (EntityClip candidate : track.getClips()) {
            if (candidate instanceof EntityAnimationClip animation && !animation.isMuted()
                    && time >= animation.getStartTime() && time <= animation.getEndTime()
                    && AnimationAssetLibrary.getInstance().contains(animation.getAnimationId())
                    && (result == null || animation.getStartTime() >= result.getStartTime())) result = animation;
        }
        return result;
    }
}

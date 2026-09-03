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
import java.util.Objects;
import java.util.UUID;

/** Compact dope sheet for the active rig animation on the selected actor. */
public final class ActorAnimationDopeSheet {
    private static String selectedAssetId;
    private static String selectedBoneId;
    private static float selectedTime = -1f;

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
                    boolean selected = Objects.equals(selectedAssetId, asset.id())
                            && Objects.equals(selectedBoneId, boneId)
                            && Math.abs(selectedTime - frame.time()) <= 1.0e-5f;
                    String label = String.format("%s %.3fs  [%s]##dope:%s:%s:%f",
                            Icons.KEYFRAME, frame.time(), frame.interpolation(), asset.id(), boneId, frame.time());
                    if (ImGui.selectable(label, selected)) {
                        select(asset.id(), boneId, frame.time());
                        model.setCurrentTime(toTimelineTime(clip, frame.time()));
                    }
                }
                ImGui.treePop();
            }
        }

        if (!Objects.equals(selectedAssetId, asset.id())) return;
        BoneTrack selectedTrack = asset.findBoneTrack(selectedBoneId).orElse(null);
        if (selectedTrack == null || selectedTrack.rotation().findAt(selectedTime).isEmpty()) {
            clearSelection();
            return;
        }
        float localFrame = Math.max(0.01f, clip.getSpeed()) / Math.max(1f, model.getFrameRate());
        if (ImGui.smallButton(Icons.ARROW_LEFT + " 1帧##dopePrevious")) {
            float target = Math.max(0f, selectedTime - localFrame);
            if (ActorAnimationKeyframeEditing.moveRotation(model, selectedTrack, selectedTime, target)) {
                selectedTime = target;
                model.setCurrentTime(toTimelineTime(clip, target));
            }
        }
        ImGui.sameLine();
        if (ImGui.smallButton(Icons.ARROW_RIGHT + " 1帧##dopeNext")) {
            float target = Math.min(asset.duration(), selectedTime + localFrame);
            if (ActorAnimationKeyframeEditing.moveRotation(model, selectedTrack, selectedTime, target)) {
                selectedTime = target;
                model.setCurrentTime(toTimelineTime(clip, target));
            }
        }
        ImGui.sameLine();
        if (ImGui.smallButton(Icons.DELETE + " 删除##dopeDelete")) {
            if (ActorAnimationKeyframeEditing.deleteRotation(model, selectedTrack, selectedTime)) clearSelection();
        }
    }

    private static float toTimelineTime(EntityAnimationClip clip, float localTime) {
        return clip.getStartTime() + localTime / Math.max(0.01f, clip.getSpeed());
    }

    private static void select(String assetId, String boneId, float time) {
        selectedAssetId = assetId;
        selectedBoneId = boneId;
        selectedTime = time;
    }

    private static void clearSelection() {
        selectedAssetId = null;
        selectedBoneId = null;
        selectedTime = -1f;
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

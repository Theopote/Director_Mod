package com.director.ui.actor.pose;

import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.*;
import com.director.actor.query.ActorUsageQuery;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.entity.EntityAnimationClip;
import imgui.ImGui;
import java.util.List;
import java.util.UUID;

/** Bone hierarchy plus explicit, undoable asset-local pose editing. */
public final class ActorBonePropertiesPanel {
    private static final ActorBonePropertiesPanel PROPERTIES = new ActorBonePropertiesPanel();
    private static final ActorBonePropertiesPanel TIMELINE = new ActorBonePropertiesPanel();
    private final BoneTransformDraft draft = new BoneTransformDraft();
    private String message = "";
    private record EditContext(TimelineModel model, UUID actorId, EntityAnimationClip clip,
                               AnimationAsset asset, String boneId, float timelineTime, float assetTime) {}
    private ActorBonePropertiesPanel() {}

    public static void render(TimelineModel model, UUID actorId) {
        PROPERTIES.renderContents(model, actorId);
    }

    public static void renderInTimeline(TimelineModel model, UUID actorId) {
        ActorBoneDraftPanel.render();
        TIMELINE.renderContents(model, actorId);
    }

    private void renderContents(TimelineModel model, UUID actorId) {
        if (model == null || actorId == null) { draft.clear(); return; }
        var actor = com.director.actor.ActorRegistryManager.getInstance().getActor(actorId).orElse(null);
        var actorRig = actor == null ? null : com.director.actor.rig.ActorRigCompatibility.resolveRig(actor).orElse(null);
        if (actorRig == null) {
            draft.clear();
            ImGui.textWrapped(actor == null ? "演员不存在" : com.director.actor.rig.ActorRigCompatibility.explanation(actor));
            return;
        }
        if (!ImGui.treeNode("骨骼与姿态关键帧##actorBones")) { draft.clear(); return; }
        var selection = ActorBoneSelectionController.getInstance();
        renderChildren(actorRig, -1, selection, actorId);
        if (!selection.isActive() || !actorId.equals(selection.getActorId())) {
            draft.clear();
            ImGui.textWrapped("点击身体部位进入姿态编辑，也可以在演员轨道的子行选择部位。");
            ImGui.treePop();
            return;
        }
        ImGui.textWrapped("Ctrl+点击可多选部位；数值和 Gizmo 编辑最后选中的部位，打帧与恢复姿态作用于全部所选部位。");
        ImGui.textDisabled("已选择 " + selection.getSelectedBoneIds().size() + " 个部位");
        var tracks = ActorUsageQuery.findTimelineTracksUsingActor(model, actorId);
        EntityAnimationClip active = null;
        com.director.timeline.model.TimelineTrack activeTrack = null;
        for (var track : tracks) for (var clip : track.getActorTrackPayload().getClips()) {
            if (clip instanceof EntityAnimationClip animation && !clip.isMuted()
                    && model.getCurrentTime() >= clip.getStartTime() && model.getCurrentTime() <= clip.getEndTime()
                    && (active == null || clip.getStartTime() >= active.getStartTime())) { active = animation; activeTrack = track; }
        }
        AnimationAsset asset = active == null ? null : AnimationAssetLibrary.getInstance().find(active.getAnimationId()).orElse(null);
        String boneId = selection.getSelectedBoneId();
        if (asset == null || boneId == null || !asset.rigId().equals(actorRig.id())) {
            draft.clear();
            ImGui.textWrapped("请先将匹配此演员骨架的动画资产放到演员轨道，并将播放头移入片段。");
            ImGui.treePop();
            return;
        }
        if (activeTrack.isLocked()) {
            draft.clear();
            ImGui.textWrapped("演员轨道已锁定，请解锁后编辑姿态关键帧。");
            ImGui.treePop();
            return;
        }
        float raw = (model.getCurrentTime() - active.getStartTime()) * Math.max(0.01f, active.getSpeed());
        float time = asset.normalizeTime(raw, active.isLoop() ? asset.loopMode() : AnimationLoopMode.ONCE);
        RigPose pose = asset.sample(time, AnimationLoopMode.ONCE);
        BoneDefinition bone = asset.rig().bone(asset.rig().requireBoneIndex(boneId));
        BoneTransform value = pose.localTransform(boneId);
        if (draft.synchronize(new EditContext(model, actorId, active, asset, boneId,
                model.getCurrentTime(), time), value)) message = "编辑对象或姿态已变化，未应用的数值已取消。";
        ImGui.textDisabled(asset.name() + " · " + String.format("%.3fs", time));
        ImGui.textWrapped("显示资产局部姿态；编辑后点击应用并打帧。切换骨骼、演员或播放头会取消未应用的数值。共享资产的修改影响所有引用。");
        int changed = 0;
        if (bone.capabilities().translation() && ImGui.inputFloat3("位置 XYZ##boneT", draft.translation, "%.3f")) changed |= RigPose.TRANSLATION;
        if (bone.capabilities().rotation() && !HumanoidRig.ROOT.equals(boneId)
                && ImGui.inputFloat3("旋转 XYZ（度）##boneR", draft.rotation, "%.2f")) changed |= RigPose.ROTATION;
        if (bone.capabilities().scale() && ImGui.inputFloat3("缩放 XYZ##boneS", draft.scale, "%.3f")) changed |= RigPose.SCALE;
        if (changed != 0) { draft.markChannelsChanged(changed); message = ""; }
        var mode = ActorAnimationAuthoringSettings.getInstance().getInterpolation();
        if (draft.isDirty()) {
            if (!draft.isValid()) ImGui.textWrapped("请输入有限数值，且缩放必须大于零。");
            if (ImGui.button("应用并打帧##applyBone") && draft.isValid()) {
                pose.setLocalTransform(boneId, draft.value());
                ActorPoseKeyframeEditing.keyChannels(model, asset, time, pose, List.of(boneId), mode, draft.changedChannels());
                draft.clear();
                message = "已应用，可一次撤销整次修改。";
            }
            ImGui.sameLine();
            if (ImGui.button("取消##cancelBone")) { draft.reset(); message = "已取消数值修改。"; }
        }
        if (!message.isEmpty()) ImGui.textWrapped(message);
        if (draft.isDirty()) {
            ImGui.textWrapped("请先应用或取消数值修改，再进行姿态打帧。");
            ImGui.treePop();
            return;
        }
        ImGui.textDisabled("按通道给所选部位打帧（仅写入支持的通道）");
        if (ImGui.button("位置##keyBoneT")) ActorPoseKeyframeEditing.keyChannels(model, asset, time, pose,
                selection.getSelectedBoneIds(), mode, RigPose.TRANSLATION);
        ImGui.sameLine();
        if (ImGui.button("旋转##keyBoneR")) ActorPoseKeyframeEditing.keyChannels(model, asset, time, pose,
                selection.getSelectedBoneIds(), mode, RigPose.ROTATION);
        ImGui.sameLine();
        if (ImGui.button("缩放##keyBoneS")) ActorPoseKeyframeEditing.keyChannels(model, asset, time, pose,
                selection.getSelectedBoneIds(), mode, RigPose.SCALE);
        if (ImGui.button("所选部位批量打帧##keyBone")) ActorPoseKeyframeEditing.key(model, asset, time, pose, selection.getSelectedBoneIds(), mode);
        if (ImGui.button("全身打帧##keyPose")) ActorPoseKeyframeEditing.key(model, asset, time, pose,
                asset.rig().bones().stream().map(BoneDefinition::id).toList(), mode);
        if (ImGui.button("所选部位恢复绑定姿态并打帧##restBone")) {
            for (String id : selection.getSelectedBoneIds())
                pose.setLocalTransform(id, asset.rig().bone(asset.rig().requireBoneIndex(id)).restLocal());
            ActorPoseKeyframeEditing.key(model, asset, time, pose, selection.getSelectedBoneIds(), mode);
        }
        ImGui.treePop();
    }

    private static void renderChildren(RigDefinition rig, int parent, ActorBoneSelectionController selection, UUID actorId) {
        for (int i = 0; i < rig.boneCount(); i++) {
            BoneDefinition bone = rig.bone(i);
            if (bone.parentIndex() != parent) continue;
            if (ImGui.selectable(bone.displayName() + "##bone:" + bone.id(), actorId.equals(selection.getActorId()) && selection.isBoneSelected(bone.id())))
                com.director.ui.actor.selection.ActorSelectionHub.selectBone(actorId, bone.id(), ImGui.getIO().getKeyCtrl());
            ImGui.indent();
            renderChildren(rig, i, selection, actorId);
            ImGui.unindent();
        }
    }
}

package com.director.ui.actor.pose;

import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.*;
import com.director.actor.query.ActorUsageQuery;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.entity.EntityAnimationClip;
import imgui.ImGui;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.List;
import java.util.UUID;

/** Bone hierarchy plus explicit, undoable asset-local pose editing. */
public final class ActorBonePropertiesPanel {
    private ActorBonePropertiesPanel() {}

    public static void render(TimelineModel model, UUID actorId) {
        if (model == null || actorId == null || !ImGui.treeNode("骨骼与姿态关键帧##actorBones")) return;
        var selection = ActorBoneSelectionController.getInstance();
        if (!selection.isActive()) selection.activate(actorId);
        else selection.synchronizeActor(actorId);
        renderChildren(HumanoidRig.DEFINITION, -1, selection);
        var tracks = ActorUsageQuery.findTracksUsingActor(model, actorId);
        EntityAnimationClip active = null;
        for (var track : tracks) for (var clip : track.getClips()) {
            if (clip instanceof EntityAnimationClip animation && !clip.isMuted()
                    && model.getCurrentTime() >= clip.getStartTime() && model.getCurrentTime() <= clip.getEndTime()
                    && (active == null || clip.getStartTime() >= active.getStartTime())) active = animation;
        }
        AnimationAsset asset = active == null ? null : AnimationAssetLibrary.getInstance().find(active.getAnimationId()).orElse(null);
        String boneId = selection.getSelectedBoneId();
        if (asset == null || boneId == null || !asset.rigId().equals(HumanoidRig.DEFINITION.id())) {
            ImGui.textWrapped("请先将人形动画资产放到演员轨道，并将播放头移入片段。");
            ImGui.treePop();
            return;
        }
        float raw = (model.getCurrentTime() - active.getStartTime()) * Math.max(0.01f, active.getSpeed());
        float time = asset.normalizeTime(raw, active.isLoop() ? asset.loopMode() : AnimationLoopMode.ONCE);
        RigPose pose = asset.sample(time, AnimationLoopMode.ONCE);
        BoneDefinition bone = asset.rig().bone(asset.rig().requireBoneIndex(boneId));
        BoneTransform value = pose.localTransform(boneId);
        Vector3f t = value.translation(), s = value.scale();
        Vector3f angles = value.rotation().getEulerAnglesZYX(new Vector3f()).mul((float)(180 / Math.PI));
        float[] translation = {t.x, t.y, t.z};
        float[] rotation = {angles.x, angles.y, angles.z};
        float[] scale = {s.x, s.y, s.z};
        ImGui.textDisabled(asset.name() + " · " + String.format("%.3fs", time));
        ImGui.textWrapped("显示资产局部姿态；按 Enter 提交数值并打帧。全身打帧保存资产采样姿态。共享资产的修改影响所有引用。");
        boolean changed = false;
        int flags = imgui.flag.ImGuiInputTextFlags.EnterReturnsTrue;
        if (bone.capabilities().translation()) changed |= ImGui.inputFloat3("位置 XYZ##boneT", translation, "%.3f", flags);
        boolean root = HumanoidRig.ROOT.equals(boneId);
        boolean rotationChanged = !root && ImGui.inputFloat3("旋转 XYZ（度）##boneR", rotation, "%.2f", flags);
        changed |= rotationChanged;
        if (bone.capabilities().scale()) changed |= ImGui.inputFloat3("缩放 XYZ##boneS", scale, "%.3f", flags);
        var mode = ActorAnimationAuthoringSettings.getInstance().getInterpolation();
        boolean valid = new Vector3f(translation).isFinite() && new Vector3f(rotation).isFinite()
                && new Vector3f(scale).isFinite() && scale[0] > 0 && scale[1] > 0 && scale[2] > 0;
        if (changed && !valid) ImGui.textWrapped("请输入有限数值，且缩放必须大于零。");
        if (valid && changed) {
            Quaternionf q = rotationChanged ? new Quaternionf().rotationZYX(
                    (float)Math.toRadians(rotation[2]), (float)Math.toRadians(rotation[1]), (float)Math.toRadians(rotation[0])) : value.rotation();
            pose.setLocalTransform(boneId, new BoneTransform(new Vector3f(translation), q, new Vector3f(scale)));
            ActorPoseKeyframeEditing.key(model, asset, time, pose, List.of(boneId), mode);
        }
        if (ImGui.button("选中骨骼打帧##keyBone")) ActorPoseKeyframeEditing.key(model, asset, time, pose, List.of(boneId), mode);
        if (ImGui.button("全身打帧##keyPose")) ActorPoseKeyframeEditing.key(model, asset, time, pose,
                asset.rig().bones().stream().map(BoneDefinition::id).toList(), mode);
        if (ImGui.button("选中骨骼恢复绑定姿态并打帧##restBone")) {
            pose.setLocalTransform(boneId, bone.restLocal());
            ActorPoseKeyframeEditing.key(model, asset, time, pose, List.of(boneId), mode);
        }
        ImGui.treePop();
    }

    private static void renderChildren(RigDefinition rig, int parent, ActorBoneSelectionController selection) {
        for (int i = 0; i < rig.boneCount(); i++) {
            BoneDefinition bone = rig.bone(i);
            if (bone.parentIndex() != parent) continue;
            if (ImGui.selectable(bone.displayName() + "##bone:" + bone.id(), bone.id().equals(selection.getSelectedBoneId())))
                selection.selectBone(bone.id());
            ImGui.indent();
            renderChildren(rig, i, selection);
            ImGui.unindent();
        }
    }
}

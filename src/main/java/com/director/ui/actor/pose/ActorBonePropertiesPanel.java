package com.director.ui.actor.pose;

import com.director.actor.animation.asset.*;
import com.director.actor.rig.skeleton.*;
import com.director.timeline.model.TimelineModel;
import imgui.ImGui;
import java.util.List;
import java.util.UUID;

/** Bone hierarchy plus explicit, undoable asset-local pose editing. */
public final class ActorBonePropertiesPanel {
    private static final ActorBonePropertiesPanel PROPERTIES = new ActorBonePropertiesPanel();
    private static final ActorBonePropertiesPanel TIMELINE = new ActorBonePropertiesPanel();
    private static final BoneTransformDraft draft = new BoneTransformDraft();
    private static NumericContext numericContext;
    private String message = "";
    record NumericContext(ActorAnimationEditContext context, String boneId,
                                  ActorAnimationAuthoringSettings.EditTarget target, BoneKeys keys, long selectionRevision, long settingsRevision) {
        static NumericContext capture(ActorAnimationEditContext context,String bone) {
            var selection=ActorBoneSelectionController.getInstance(); var settings=ActorAnimationAuthoringSettings.getInstance();
            return new NumericContext(context,bone,settings.getEditTarget(),BoneKeys.capture(context.asset(),bone),selection.getRevision(),settings.getRevision());
        }
        boolean isCurrent() {
            var selection=ActorBoneSelectionController.getInstance(); var settings=ActorAnimationAuthoringSettings.getInstance();
            return target==ActorAnimationAuthoringSettings.EditTarget.ANIMATION && context.isCurrent() && selection.isActive() && selectionRevision==selection.getRevision()
                    && settingsRevision==settings.getRevision() && context.actorId().equals(selection.getActorId())
                    && boneId.equals(selection.getSelectedBoneId()) && target==settings.getEditTarget()
                    && keys.equals(BoneKeys.capture(context.asset(),boneId));
        }
    }
    private record BoneKeys(List<?> translation, List<?> rotation, List<?> scale) {
        static BoneKeys capture(AnimationAsset asset, String boneId) {
            var track = asset.findBoneTrack(boneId).orElse(null);
            return track == null ? new BoneKeys(List.of(), List.of(), List.of())
                    : new BoneKeys(track.translation().keyframes(), track.rotation().keyframes(), track.scale().keyframes());
        }
    }
    private ActorBonePropertiesPanel() {}
    public static boolean hasNumericDraft() {
        if (draft.isDirty()) {
            if (numericContext == null || !numericContext.isCurrent()
                    || !draft.matchesSource(numericContext.context().asset()
                        .sample(numericContext.context().assetTime(), AnimationLoopMode.ONCE)
                        .localTransform(numericContext.boneId()))) cancelNumericDraft();
        }
        return draft.isDirty();
    }
    public static void cancelNumericDraft() { draft.clear(); numericContext = null; }


    public static void render(TimelineModel model, UUID actorId) {
        PROPERTIES.renderContents(model, actorId);
    }

    public static void renderInTimeline(TimelineModel model, UUID actorId) {
        ActorBoneDraftPanel.render();
        TIMELINE.renderContents(model, actorId);
    }

    private void renderContents(TimelineModel model, UUID actorId) {
        hasNumericDraft();
        if (model == null || actorId == null) { draft.clear(); return; }
        var actor = com.director.actor.ActorRegistryManager.getInstance().getActor(actorId).orElse(null);
        var actorRig = actor == null ? null : com.director.actor.rig.ActorRigCompatibility.resolveRig(actor).orElse(null);
        if (actorRig == null) {
            draft.clear();
            ImGui.textWrapped(actor == null ? "演员不存在" : com.director.actor.rig.ActorRigCompatibility.explanation(actor));
            return;
        }
        // The other inspector may be open and editing this same draft.
        if (!ImGui.treeNode("骨骼与姿态关键帧##actorBones")) return;
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
        if(ActorAnimationAuthoringSettings.getInstance().getEditTarget()!=ActorAnimationAuthoringSettings.EditTarget.ANIMATION) {
            cancelNumericDraft();
            ImGui.textWrapped(net.minecraft.client.resource.language.I18n.translate("gui.director.actor.authoring.numeric_asset_target"));
            ImGui.treePop();
            return;
        }
        if (ActorBoneGizmoController.getInstance().isDragging()) {
            draft.clear();
            ImGui.textWrapped(net.minecraft.client.resource.language.I18n.translate("gui.director.actor.authoring.gizmo_pending"));
            ImGui.treePop();
            return;
        }
        ActorAnimationEditContext context = ActorAnimationEditContext.resolve(model, actorId);
        String boneId = selection.getSelectedBoneId();
        if (context == null || boneId == null || !context.isCurrent()) {
            draft.clear();
            ImGui.textWrapped(net.minecraft.client.resource.language.I18n.translate("gui.director.actor.authoring.unavailable"));
            ImGui.treePop();
            return;
        }
        AnimationAsset asset = context.asset();
        float time = context.assetTime();
        RigPose pose = asset.sample(time, AnimationLoopMode.ONCE);
        BoneDefinition bone = asset.rig().bone(asset.rig().requireBoneIndex(boneId));
        BoneTransform value = pose.localTransform(boneId);
        numericContext = NumericContext.capture(context,boneId);
        if (draft.synchronize(numericContext, value)) message = "编辑对象或姿态已变化，未应用的数值已取消。";
        ImGui.textDisabled(asset.name() + " · " + String.format("%.3fs", time));
        ImGui.textWrapped("显示资产局部姿态；编辑后点击应用并打帧。切换骨骼、演员或播放头会取消未应用的数值。共享资产的修改影响所有引用。");
        if (ImGui.isKeyPressed(imgui.flag.ImGuiKey.Escape)) draft.reset();
        int changed = 0;
        if (bone.capabilities().translation() && ImGui.inputFloat3("位置 XYZ##boneT", draft.translation, "%.3f")) changed |= RigPose.TRANSLATION;
        if (bone.capabilities().rotation() && !(HumanoidTopology.matches(asset.rig()) && HumanoidRig.ROOT.equals(boneId))
                && ImGui.inputFloat3("旋转 XYZ（度）##boneR", draft.rotation, "%.2f")) changed |= RigPose.ROTATION;
        if (bone.capabilities().scale() && ImGui.inputFloat3("缩放 XYZ##boneS", draft.scale, "%.3f")) changed |= RigPose.SCALE;
        if (changed != 0) { draft.markChannelsChanged(changed); message = ""; }
        var mode = ActorAnimationAuthoringSettings.getInstance().getInterpolation();
        if (draft.isDirty()) {
            if (!draft.isValid()) ImGui.textWrapped("请输入有限数值，且缩放必须大于零。");
            if (ImGui.button("应用并打帧##applyBone") && draft.isValid() && hasNumericDraft()) {
                pose.setLocalTransform(boneId, draft.value());
                boolean applied = ActorAnimationAuthoringService.keyPose(context, pose, List.of(boneId), mode, draft.changedChannels());
                draft.clear();
                if (!applied) { ImGui.treePop(); return; }
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
        if (ImGui.button("位置##keyBoneT")) ActorAnimationAuthoringService.keyPose(context, pose,
                selection.getSelectedBoneIds(), mode, RigPose.TRANSLATION);
        ImGui.sameLine();
        if (ImGui.button("旋转##keyBoneR")) ActorAnimationAuthoringService.keyPose(context, pose,
                selection.getSelectedBoneIds(), mode, RigPose.ROTATION);
        ImGui.sameLine();
        if (ImGui.button("缩放##keyBoneS")) ActorAnimationAuthoringService.keyPose(context, pose,
                selection.getSelectedBoneIds(), mode, RigPose.SCALE);
        if (ImGui.button("所选部位批量打帧##keyBone")) ActorAnimationAuthoringService.keyPose(context, pose, selection.getSelectedBoneIds(), mode, RigPose.ALL_CHANNELS);
        if (ImGui.button("全身打帧##keyPose")) ActorAnimationAuthoringService.keyPose(context, pose,
                asset.rig().bones().stream().map(BoneDefinition::id).toList(), mode, RigPose.ALL_CHANNELS);
        if (ImGui.button("所选部位恢复绑定姿态并打帧##restBone")) {
            for (String id : selection.getSelectedBoneIds())
                pose.setLocalTransform(id, asset.rig().bone(asset.rig().requireBoneIndex(id)).restLocal());
            ActorAnimationAuthoringService.keyPose(context, pose, selection.getSelectedBoneIds(), mode, RigPose.ALL_CHANNELS);
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

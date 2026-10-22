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
    private boolean presetSelectedOnly = true;
    private ActorSavedPoseLibrary.Saved editingSavedPose;
    private boolean deletingSavedPose;
    private List<ActorSavedPoseLibrary.Saved> savedPoseEditSource;
    private final imgui.type.ImString renameSavedPose = new imgui.type.ImString(128);
    private final imgui.type.ImString savedPoseName = new imgui.type.ImString(128);
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
    /** Apply the numeric draft to the authored input, before masks, fades and constraints. */
    public static RigPose previewAuthoredPose(UUID actorId, String assetId, RigPose sampled) {
        if (!hasNumericDraft() || numericContext == null || !numericContext.context().actorId().equals(actorId)
                || !numericContext.context().asset().id().equals(assetId)
                || sampled == null || !sampled.rig().id().equals(numericContext.context().asset().rigId())) return sampled;
        return draft.preview(sampled, numericContext.boneId());
    }



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
        String activeBone = selection.getSelectedBoneId();
        if (activeBone != null) renderSelectionControls(model, actorId, actorRig, activeBone);
        if(ActorAnimationAuthoringSettings.getInstance().getEditTarget()!=ActorAnimationAuthoringSettings.EditTarget.ANIMATION) {
            cancelNumericDraft();
            ImGui.textWrapped(net.minecraft.client.resource.language.I18n.translate("gui.director.actor.authoring.numeric_asset_target"));
            ImGui.treePop();
            return;
        }
        ActorBoneInfluenceUI.render(model,actorId,actorRig,activeBone,
                hasNumericDraft() || ActorBoneGizmoController.getInstance().isDragging());
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
            var owner = ActorAnimationAssetEditing.resolveActorTrack(model, actorId);
            if (context == null && boneId != null && ActorEmbeddedAnimation.target(model, owner) != null) {
                ImGui.textWrapped("此演员尚未建立骨骼关键帧。选择一个通道开始编辑，动画会保存在原演员片段中。");
                for (int channel : new int[]{RigPose.TRANSLATION, RigPose.ROTATION, RigPose.SCALE}) {
                    if (!ActorBoneInspector.supports(actorRig, boneId, channel)) continue;
                    if (ImGui.button("建立" + ActorBoneInspector.label(channel) + "关键帧##firstBone" + channel))
                        ActorEmbeddedAnimation.firstKeyFromPreview(model, owner, boneId, channel,
                                ActorAnimationAuthoringSettings.getInstance().getInterpolation());
                }
            } else ImGui.textWrapped(net.minecraft.client.resource.language.I18n.translate("gui.director.actor.authoring.unavailable"));
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
        ImGui.textWrapped("显示资产局部姿态；编辑后点击应用并打帧。切换骨骼、演员或播放头会取消未应用的数值。共享资产的修改影响所有引用。世界中显示混合后的结果；低权重、淡入淡出或被排除的通道会减弱或隐藏修改。");
        if (ImGui.isKeyPressed(imgui.flag.ImGuiKey.Escape)) draft.reset();
        int changed = 0;
        if (bone.capabilities().translation() && ImGui.inputFloat3("位置 XYZ##boneT", draft.translation, "%.3f")) changed |= RigPose.TRANSLATION;
        boolean joint = ActorJointBend.isJoint(boneId);
        if (joint) {
            if(!draft.isDirty()) {
                var angles=LimbBendTransform.angles(value.rotation());
                draft.rotation[0]=(float)Math.toDegrees(angles.x);
                draft.rotation[1]=(float)Math.toDegrees(angles.y);
                draft.rotation[2]=0;
            }
            float maxBend=160f;
            float[] bend = {ActorJointBend.degrees(boneId, draft.rotation[0])};
            ImGui.textDisabled("局部关节弯曲；修改后点击应用并打帧。");
            if (bend[0] < 0 || bend[0] > maxBend)
                ImGui.textWrapped("已保留原关键帧角度；当前模式的显示范围为 0–"+(int)maxBend+" 度。");
            if (ImGui.sliderFloat("弯曲角度（度）##jointBend", bend, 0f, maxBend, "%.1f")) {
                ActorJointBend.set(boneId, bend[0], draft.rotation);
                changed |= RigPose.ROTATION;
            }
            if (ImGui.smallButton("恢复伸直##straightJoint")) {
                ActorJointBend.set(boneId, 0f, draft.rotation);
                changed |= RigPose.ROTATION;
            }
            float[] twist={draft.rotation[1]};
            if (ImGui.sliderFloat("弯曲方向 Y（度）##jointTwist",twist,-90f,90f,"%.1f")) {
                draft.rotation[1]=twist[0]; draft.rotation[2]=0; changed |= RigPose.ROTATION;
            }
            ImGui.textDisabled("X 控制弯曲角度，Y 改变弯曲平面，不扭转皮肤。");
        } else if (bone.capabilities().rotation() && !(HumanoidTopology.matches(asset.rig()) && HumanoidRig.ROOT.equals(boneId))
                && ImGui.inputFloat3("旋转 XYZ（度）##boneR", draft.rotation, "%.2f")) changed |= RigPose.ROTATION;
        if (bone.capabilities().scale() && ImGui.inputFloat3("缩放 XYZ##boneS", draft.scale, "%.3f")) changed |= RigPose.SCALE;
        if (changed != 0) {
            com.director.ui.timeline.render.ActorBoneTimelineRows.cancelAllKeyInteractions();
            ActorAnimationDopeSheet.cancelKeyInteraction(); ActorKeyframeTimePanel.cancelAll();
            draft.markChannelsChanged(changed); message = ""; }
        var mode = ActorAnimationAuthoringSettings.getInstance().getInterpolation();
        if (draft.isDirty()) {
            if (draft.isValid()) com.director.actor.runtime.ActorRuntimeController.getInstance().refreshPosePreview(actorId);
            ImGui.textDisabled("当前预览使用未保存的资产值，并经过动作混合及约束；应用后写入关键帧，取消则恢复。");
            if (!draft.isValid()) ImGui.textWrapped("请输入有限数值，且缩放必须大于零。");
            if (ImGui.button("应用并打帧##applyBone") && draft.isValid() && hasNumericDraft()) {
                pose.setLocalTransform(boneId, draft.value());
                boolean applied = ActorAnimationAuthoringService.keyPose(context, pose, List.of(boneId), mode, draft.changedChannels());
                draft.clear();
                com.director.actor.runtime.ActorRuntimeController.getInstance().refreshPosePreview(actorId);
                if (!applied) { ImGui.treePop(); return; }
                message = "已应用，可一次撤销整次修改。";
            }
            ImGui.sameLine();
            if (ImGui.button("取消##cancelBone")) { draft.reset(); com.director.actor.runtime.ActorRuntimeController.getInstance().refreshPosePreview(actorId); message = "已取消数值修改。"; }
        }
        if (!message.isEmpty()) ImGui.textWrapped(message);
        if (draft.isDirty()) {
            ImGui.textWrapped("请先应用或取消数值修改，再进行姿态打帧。");
            ImGui.treePop();
            return;
        }
        if (HumanoidTopology.matches(asset.rig())) {
            ImGui.separator();
            ImGui.text("姿态预设");
            if (ImGui.checkbox("仅作用于所选部位##presetScope", presetSelectedOnly)) presetSelectedOnly = !presetSelectedOnly;
            ImGui.textDisabled("在当前时间写入旋转关键帧，保留位置与缩放；这是静态姿态，不是循环动作。");
            for (var preset : ActorPosePresets.Preset.values()) {
                if (ImGui.button(preset.label() + "##posePreset" + preset.name())) {
                    var bones = presetSelectedOnly ? selection.getSelectedBoneIds()
                            : asset.rig().bones().stream().map(BoneDefinition::id).toList();
                    var presetPose = ActorPosePresets.apply(pose, preset, bones);
                    boolean applied = ActorAnimationAuthoringService.keyPose(context, presetPose, bones, mode, RigPose.ROTATION);
                    message = applied ? "姿态已写入当前关键帧，可撤销。" : "没有可修改的旋转通道，或姿态未变化。";
                }
            }
        }
        if (ImGui.treeNode("自定义姿态库##savedPoses")) {
            ImGui.textWrapped("保存当前动画资产中的旋转姿态；不会保存自动步态、注视结果、位置或缩放。");
            ImGui.inputText("姿态名称##savedPoseName", savedPoseName);
            if (ImGui.checkbox("仅所选部位##savedPoseScope", presetSelectedOnly)) presetSelectedOnly = !presetSelectedOnly;
            var scope = presetSelectedOnly ? selection.getSelectedBoneIds()
                    : asset.rig().bones().stream().map(BoneDefinition::id).toList();
            if (ImGui.button("保存当前旋转姿态##savePose")) {
                try {
                    String name = savedPoseName.get().split("\\u0000", 2)[0];
                    ActorSavedPoseLibrary.add(ActorSavedPoseLibrary.capture(name, pose, scope));
                    message = "姿态已保存到当前存档。";
                } catch (java.io.IOException | IllegalArgumentException failure) { message = failure.getMessage(); }
            }
            try {
                for (var saved : ActorSavedPoseLibrary.current()) {
                    if (!saved.rigId().equals(asset.rigId())) continue;
                    ImGui.pushID(saved.name());
                    if (ImGui.smallButton("重命名##renameSaved")) {
                        editingSavedPose = saved; savedPoseEditSource = ActorSavedPoseLibrary.current(); deletingSavedPose = false; renameSavedPose.set(saved.name());
                    }
                    ImGui.sameLine();
                    if (ImGui.smallButton("删除##deleteSaved")) {
                        editingSavedPose = saved; savedPoseEditSource = ActorSavedPoseLibrary.current(); deletingSavedPose = true;
                    }
                    ImGui.sameLine();
                    if (ImGui.button("应用 " + saved.name() + "##savedPose:" + saved.name())) {
                        var bones = scope.stream().filter(saved.rotations()::containsKey).toList();
                        boolean applied = ActorAnimationAuthoringService.keyPose(context, saved.apply(pose,bones),
                                bones, mode, RigPose.ROTATION);
                        message = applied ? "保存的姿态已写入当前关键帧。" : "所选部位未包含在此姿态中，或姿态未变化。";
                    }
                    ImGui.popID();
                }
                if (editingSavedPose != null) {
                    if (savedPoseEditSource != ActorSavedPoseLibrary.current() || !ActorSavedPoseLibrary.current().contains(editingSavedPose)
                            || !editingSavedPose.rigId().equals(asset.rigId())) editingSavedPose = null;
                    else {
                        ImGui.separator();
                        if (deletingSavedPose) ImGui.textWrapped("删除保存的姿态：" + editingSavedPose.name()
                                + "？已写入时间线的关键帧会保留，删除可以撤销。");
                        else ImGui.inputText("新姿态名称##renameSavedName", renameSavedPose);
                        if (ImGui.button(deletingSavedPose ? "确认删除##confirmSaved" : "保存名称##confirmSaved")) {
                            try {
                                ActorSavedPoseLibrary.edit(model, editingSavedPose, deletingSavedPose ? null : renameSavedPose.get());
                                editingSavedPose = null; message = "姿态库已更新，可撤销。";
                            } catch (java.io.IOException | RuntimeException failure) { message = failure.getMessage(); }
                        }
                        ImGui.sameLine();
                        if (ImGui.button("取消##cancelSaved")) editingSavedPose = null;
                    }
                }
            } catch (java.io.IOException failure) { ImGui.textWrapped(failure.getMessage()); }
            ImGui.treePop();
        }
        ImGui.textDisabled("按通道给所选部位打帧（仅写入支持的通道）");
        ImGui.beginDisabled(selection.getSelectedBoneIds().stream().noneMatch(id ->
                ActorBoneInspector.supports(asset.rig(), id, RigPose.TRANSLATION)));
        if (ImGui.button("位置##keyBoneT")) ActorEmbeddedAnimation.keyFromPreview(context,
                selection.getSelectedBoneIds(), mode, RigPose.TRANSLATION);
        ImGui.endDisabled();
        ImGui.sameLine();
        ImGui.beginDisabled(selection.getSelectedBoneIds().stream().noneMatch(id ->
                ActorBoneInspector.supports(asset.rig(), id, RigPose.ROTATION)));
        if (ImGui.button("旋转##keyBoneR")) ActorEmbeddedAnimation.keyFromPreview(context,
                selection.getSelectedBoneIds(), mode, RigPose.ROTATION);
        ImGui.endDisabled();
        ImGui.sameLine();
        ImGui.beginDisabled(selection.getSelectedBoneIds().stream().noneMatch(id ->
                ActorBoneInspector.supports(asset.rig(), id, RigPose.SCALE)));
        if (ImGui.button("缩放##keyBoneS")) ActorEmbeddedAnimation.keyFromPreview(context,
                selection.getSelectedBoneIds(), mode, RigPose.SCALE);
        ImGui.endDisabled();
        if (ImGui.button("所选部位批量打帧##keyBone")) ActorEmbeddedAnimation.keyFromPreview(context, selection.getSelectedBoneIds(), mode, RigPose.ALL_CHANNELS);
        if (ImGui.button("全身打帧##keyPose")) ActorEmbeddedAnimation.keyFromPreview(context,
                asset.rig().bones().stream().map(BoneDefinition::id).toList(), mode, RigPose.ALL_CHANNELS);
        if (ImGui.button("所选部位恢复绑定姿态并打帧##restBone")) {
            for (String id : selection.getSelectedBoneIds())
                pose.setLocalTransform(id, asset.rig().bone(asset.rig().requireBoneIndex(id)).restLocal());
            ActorAnimationAuthoringService.keyPose(context, pose, selection.getSelectedBoneIds(), mode, RigPose.ALL_CHANNELS);
        }
        ImGui.treePop();
    }

    private static void renderSelectionControls(TimelineModel model, UUID actorId, RigDefinition rig, String boneId) {
        var settings = ActorAnimationAuthoringSettings.getInstance();
        var bone = rig.bone(rig.requireBoneIndex(boneId));
        ImGui.separator();
        ImGui.text("当前部位：" + bone.displayName());
        ImGui.textDisabled("旋转／缩放：部位局部轴；位置：父级局部轴。");
        for (int channel : new int[]{RigPose.TRANSLATION, RigPose.ROTATION, RigPose.SCALE}) {
            if (!ActorBoneInspector.supports(rig, boneId, channel)) continue;
            var mode = ActorBoneInspector.mode(channel);
            ImGui.beginDisabled(ActorBoneGizmoController.getInstance().isDragging() || hasNumericDraft());
            if (ImGui.radioButton(ActorBoneInspector.label(channel) + "##boneMode" + channel,
                    settings.getBoneGizmoMode() == mode)) settings.setBoneGizmoMode(mode);
            ImGui.endDisabled();
            ImGui.sameLine();
        }
        ImGui.newLine();
        if (ImGui.checkbox("快速姿态自动应用##boneAutoKey", settings.isAutoKeyframe()))
            settings.setAutoKeyframe(!settings.isAutoKeyframe());
        ImGui.textDisabled("骨骼动画：拖动松开即保存；快速姿态：按自动应用设置或应用草稿。");
        var context = ActorAnimationEditContext.resolve(model, actorId);
        if (context != null) {
            var track = context.asset().findBoneTrack(boneId).orElse(null);
            ImGui.textDisabled("关键帧：位置 " + (track == null ? 0 : track.translation().keyframes().size())
                    + " / 旋转 " + (track == null ? 0 : track.rotation().keyframes().size())
                    + " / 缩放 " + (track == null ? 0 : track.scale().keyframes().size()));
        }
        var owner = ActorAnimationAssetEditing.resolveActorTrack(model, actorId);
        if (owner != null) {
            var sample = new com.director.timeline.playback.entity.EntityClipSampler(owner.getActorTrackPayload())
                    .sampleSnapshot(model.getCurrentTime());
            RigPose authored = new RigPose(rig);
            var asset = AnimationAssetLibrary.getInstance().find(sample.animationId)
                    .filter(value -> value.rigId().equals(rig.id())).orElse(null);
            if (asset != null) authored = asset.sample(sample.animationLocalTime * Math.max(.01f,sample.animationSpeed),
                    sample.animationLoop ? asset.loopMode() : AnimationLoopMode.ONCE);
            ImGui.textDisabled("局部通道控制来源（不含未保存草稿）");
            for (int channel : new int[]{RigPose.TRANSLATION,RigPose.ROTATION,RigPose.SCALE}) {
                if (ActorBoneInspector.supports(rig,boneId,channel))
                    ImGui.textWrapped(ActorBoneInspector.label(channel)+"："+ActorBoneControlSource.source(boneId,channel,sample,authored));
            }
            if (sample.routeClip != null) ImGui.textDisabled("整体移动：沿路径；路径只控制整体，不直接控制局部骨骼。");
            if (HumanoidRig.HEAD.equals(boneId) && sample.lookAtEnabled && sample.lookAtStrength > 0)
                ImGui.textWrapped("注视约束正在控制头部朝向。制作手工转头动画时，请关闭注视约束。");
        }

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

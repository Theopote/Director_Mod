package com.director.ui.actor.pose;

import com.director.actor.animation.asset.AnimationAsset;
import com.director.actor.animation.asset.AnimationAssetLibrary;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.entity.EntityAnimationClip;
import net.minecraft.client.resource.language.I18n;
import imgui.ImGui;
import imgui.type.ImString;
import java.util.UUID;

/** Project animation browser embedded in the Actor Pose tool. */
public final class ActorAnimationAssetPanel {
    private static final ImString targetId = new ImString(256);
    private static final ImString name = new ImString(256);
    private static final ImString importId = new ImString(256);
    private static AnimationAsset selected;
    private static String message = "";
    private static TimelineModel ownerModel;

    private ActorAnimationAssetPanel() {}

    public static void render(TimelineModel model, UUID actorId) {
        if (model == null || !ImGui.treeNode("动画资产库##actorAssets")) return;
        var library = AnimationAssetLibrary.getInstance();
        var settings = ActorAnimationAuthoringSettings.getInstance();
        if (ownerModel != model) {
            ownerModel = model;
            selected = null;
            name.set(""); importId.set(""); message = "";
            targetId.set(ActorAnimationAssetEditing.uniqueId(library, "project:animation"));
        }
        if (selected != null && library.find(selected.id()).orElse(null) != selected) {
            selected = null;
            message = "";
        }
        for (AnimationAsset asset : library.all()) {
            if (ImGui.selectable(asset.name() + "##asset:" + asset.id(), selected == asset)) {
                selectAsset(asset, library);
                message = "";
            }
        }
        if (selected != null) {
            ImGui.textDisabled(selected.id() + " · " + selected.duration() + "s");
            ImGui.textDisabled(I18n.translate("gui.director.actor.assets.rig") + selected.rig().displayName());
            ImGui.textDisabled("时间线引用：" + ActorAnimationAssetEditing.referenceCount(model, selected.id()));
            ImGui.textWrapped("编辑此资产会影响所有引用它的片段。需要独立修改时，请先复制。");
        }
        boolean readOnly = model.isPlaying() || model.isScrubbing() || ActorBoneGizmoController.getInstance().isDragging()
                || ActorBonePropertiesPanel.hasNumericDraft();
        if (readOnly) ImGui.textWrapped(I18n.translate("gui.director.actor.assets.read_only"));
        var actor = actorId == null ? null : com.director.actor.ActorRegistryManager.getInstance().getActor(actorId).orElse(null);
        var rig = actor == null ? null : com.director.actor.rig.ActorRigCompatibility.resolveRig(actor).orElse(null);
        if (actorId != null && rig == null) ImGui.textWrapped(actor == null
                ? I18n.translate("gui.director.actor.authoring.unavailable")
                : com.director.actor.rig.ActorRigCompatibility.explanation(actor));
        if (actorId == null || rig != null) ImGui.textDisabled(I18n.translate("gui.director.actor.assets.new_rig",
                rig == null ? com.director.actor.rig.skeleton.HumanoidRig.DEFINITION.displayName() : rig.displayName()));
        ImGui.beginDisabled(readOnly);
        if(selected!=null) {
            var purposes=com.director.actor.animation.AnimationData.Purpose.values();
            var purpose=new imgui.type.ImInt(selected.purpose().ordinal());
            if(ImGui.combo("动作用途##assetPurpose",purpose,java.util.Arrays.stream(purposes).map(p->p.label()).toArray(String[]::new)))
                run(()->ActorAnimationAssetEditing.setPurpose(model,library,selected.id(),purposes[purpose.get()]));
            ImGui.textWrapped("循环步态沿路径时按行进距离播放；表演和静态姿态保留片段时钟。");
        }
        ImGui.inputText("名称##assetName", name);
        ImGui.inputText("新建/副本 ID##assetTargetId", targetId);
        ImGui.beginDisabled(actorId != null && rig == null);
        if (ImGui.button("新建##assetCreate")) run(() -> selectAsset(ActorAnimationAssetEditing.createForActor(model, library, targetId.get().trim(),
                name.get(), settings.getNewAssetDuration(), actorId), library));
        ImGui.endDisabled();
        if (selected != null) {
            ImGui.sameLine();
            if (ImGui.button("复制##assetDuplicate")) run(() -> selectAsset(ActorAnimationAssetEditing.duplicate(model, library, selected.id(), targetId.get().trim(), name.get()), library));
            if (ImGui.button("重命名##assetRename")) run(() ->
                    ActorAnimationAssetEditing.rename(model, library, selected.id(), name.get()));
            ImGui.sameLine();
            ImGui.beginDisabled(ActorAnimationAssetEditing.referenceCount(model, selected.id()) > 0);
            if (ImGui.button("删除未引用资产##assetDelete")) run(() -> {
                ActorAnimationAssetEditing.delete(model, library, selected.id());
                selected = null;
            });
            ImGui.endDisabled();
            if (selected == null) {
                ImGui.endDisabled();
                ImGui.treePop();
                return;
            }
            TimelineTrack target = null;
            com.director.timeline.model.entity.EntitySpawnClip preview = null;
            String reason = "";
            try {
                target = ActorAnimationAssetEditing.resolveActorTrack(model, actorId);
                preview = ActorAnimationAssetEditing.previewPresetAttachment(model, library, selected.id(), target);
            } catch (IllegalArgumentException e) { reason = e.getMessage(); }
            if (preview != null) {
                ImGui.textWrapped("应用到片段：" + preview.getName() + "。保留手动骨骼关键帧。");
                if (preview.getPreset() != null)
                    ImGui.textWrapped("将替换此片段当前的动作预设，可撤销。");
            }
            if (!reason.isEmpty()) ImGui.textWrapped(reason);
            ImGui.beginDisabled(preview == null);
            if (ImGui.button("作为当前演员片段的动作预设##assetAttach")) run(() -> {
                var track = ActorAnimationAssetEditing.resolveActorTrack(model, actorId);
                ActorAnimationAssetEditing.applyPreset(model, library, selected.id(), track);
            });
            ImGui.endDisabled();
            EntityAnimationClip active = activeClip(model, target);
            if (active != null) {
                ImGui.textDisabled(I18n.translate("gui.director.actor.assets.current_clip", active.getName(), active.getAnimationId()));
                TimelineTrack owner = target;
                ImGui.beginDisabled(owner.isLocked() || owner.isMuted() || rig == null
                        || library.find(active.getAnimationId()).filter(asset -> asset.rigId().equals(rig.id())).isEmpty());
                if (ImGui.button(I18n.translate("gui.director.actor.assets.make_independent") + "##assetIndependent")) run(() ->
                        selectAsset(ActorAnimationAssetEditing.makeIndependent(model, library, owner, active,
                                targetId.get().trim(), name.get()), library));
                ImGui.endDisabled();
            }
        }
        ImGui.endDisabled();
        ImGui.separator();
        ImGui.inputText("导入 ID（留空保留原 ID）##assetImportId", importId);
        ImGui.beginDisabled(readOnly);
        if (ImGui.button("导入动画文件##assetImport")) {
            try {
                float time = model.getCurrentTime();
                String file = chooseFile(false);
                if (file != null) {
                    if (com.director.core.DirectorServices.get().containerTimeline().getTimelineModel() != model
                            || !java.util.Objects.equals(actorId, com.director.ui.actor.selection.ActorSelectionHub.getSelectedActorId())
                            || Float.compare(time, model.getCurrentTime()) != 0)
                        throw new IllegalArgumentException(I18n.translate("gui.director.actor.assets.import_context_changed"));
                    selectAsset(ActorAnimationAssetEditing.importFile(model, library,
                            java.nio.file.Path.of(file), importId.get()), library);
                    message = "导入完成，可撤销。可将此动作设为演员片段的预设。";
                }
            } catch (java.io.IOException | RuntimeException e) {
                message = "导入失败：" + e.getMessage();
                org.slf4j.LoggerFactory.getLogger(ActorAnimationAssetPanel.class).warn("Animation import failed", e);
            }
        }
        ImGui.endDisabled();
        if (selected != null && ImGui.button("导出所选动画##assetExport")) {
            try {
                String file = chooseFile(true);
                if (file != null) {
                    com.director.actor.animation.asset.AnimationAssetPersistence.exportAsset(java.nio.file.Path.of(file), selected);
                    message = "已导出至 " + file + "。导出文件不参与时间线撤销。";
                }
            } catch (java.io.IOException | RuntimeException e) {
                message = "导出失败：" + e.getMessage();
                org.slf4j.LoggerFactory.getLogger(ActorAnimationAssetPanel.class).warn("Animation export failed", e);
            }
        }
        if (!message.isEmpty()) ImGui.textWrapped(message);
        ImGui.treePop();
    }

    private static void run(Runnable operation) {
        try {
            operation.run();
            message = "操作完成，可使用时间线撤销/重做。";
        } catch (RuntimeException e) {
            message = e.getMessage();
            org.slf4j.LoggerFactory.getLogger(ActorAnimationAssetPanel.class).warn("Animation asset edit failed", e);
        }
    }

    private static void selectAsset(AnimationAsset asset, AnimationAssetLibrary library) {
        selected = asset;
        name.set(asset.name());
        targetId.set(ActorAnimationAssetEditing.uniqueId(library, asset.id() + "_copy"));
        ActorAnimationAuthoringSettings.getInstance().setAssetId(asset.id());
    }

    private static EntityAnimationClip activeClip(TimelineModel model, TimelineTrack track) {
        if (track == null || track.getActorTrackPayload() == null) return null;
        EntityAnimationClip active = null;
        for (var clip : ActorEmbeddedAnimation.clips(track.getActorTrackPayload())) {
            if (clip instanceof EntityAnimationClip animation && !animation.isMuted()
                    && model.getCurrentTime() >= clip.getStartTime() && model.getCurrentTime() <= clip.getEndTime()
                    && (active == null || clip.getStartTime() >= active.getStartTime())) active = animation;
        }
        return active;
    }

    private static String chooseFile(boolean save) {
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.json")).flip();
            return save
                    ? org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_saveFileDialog(
                            "导出动画（请选择新文件名）", "animation.json", filters, "Director animation JSON")
                    : org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(
                            "导入 Director 动画", "", filters, "Director animation JSON", false);
        }
    }
}

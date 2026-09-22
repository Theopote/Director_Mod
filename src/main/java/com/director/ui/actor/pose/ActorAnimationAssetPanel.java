package com.director.ui.actor.pose;

import com.director.actor.animation.asset.AnimationAsset;
import com.director.actor.animation.asset.AnimationAssetLibrary;
import com.director.timeline.model.TimelineModel;
import com.director.actor.query.ActorUsageQuery;
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

    private ActorAnimationAssetPanel() {}

    public static void render(TimelineModel model, UUID actorId) {
        if (model == null || !ImGui.treeNode("动画资产库##actorAssets")) return;
        var library = AnimationAssetLibrary.getInstance();
        var settings = ActorAnimationAuthoringSettings.getInstance();
        if (selected != null && library.find(selected.id()).orElse(null) != selected) {
            selected = null;
            message = "";
        }
        for (AnimationAsset asset : library.all()) {
            if (ImGui.selectable(asset.name() + "##asset:" + asset.id(), selected == asset)) {
                selected = asset;
                name.set(asset.name());
                targetId.set(asset.id() + "_copy");
                settings.setAssetId(asset.id());
                message = "";
            }
        }
        if (selected != null) {
            ImGui.textDisabled(selected.id() + " · " + selected.duration() + "s");
            ImGui.textDisabled("时间线引用：" + ActorAnimationAssetEditing.referenceCount(model, selected.id()));
            ImGui.textWrapped("编辑此资产会影响所有引用它的片段。需要独立修改时，请先复制。");
        }
        ImGui.inputText("名称##assetName", name);
        ImGui.inputText("新建/副本 ID##assetTargetId", targetId);
        if (ImGui.button("新建##assetCreate")) run(() -> {
            var actor = actorId == null ? null : com.director.actor.ActorRegistryManager.getInstance().getActor(actorId).orElse(null);
            var rig = actor == null ? null : com.director.actor.rig.ActorRigCompatibility.resolveRig(actor).orElse(null);
            if (rig == null) {
                selected = ActorAnimationAssetEditing.create(model, library, targetId.get().trim(), name.get(), settings.getNewAssetDuration());
            } else {
                selected = ActorAnimationAssetEditing.create(model, library, targetId.get().trim(), name.get(), settings.getNewAssetDuration(), rig);
            }
            settings.setAssetId(selected.id());
        });
        if (selected != null) {
            ImGui.sameLine();
            if (ImGui.button("复制##assetDuplicate")) run(() -> {
                selected = ActorAnimationAssetEditing.duplicate(model, library, selected.id(), targetId.get().trim(), name.get());
                settings.setAssetId(selected.id());
            });
            if (ImGui.button("重命名##assetRename")) run(() ->
                    ActorAnimationAssetEditing.rename(model, library, selected.id(), name.get()));
            ImGui.sameLine();
            if (ImGui.button("删除未引用资产##assetDelete")) run(() -> {
                ActorAnimationAssetEditing.delete(model, library, selected.id());
                selected = null;
            });
            if (ImGui.button("放到所选演员的播放头##assetAttach")) run(() -> {
                var tracks = ActorUsageQuery.findTimelineTracksUsingActor(model, actorId);
                if (tracks.size() != 1) throw new IllegalArgumentException("需要所选演员恰好对应一条轨道");
                ActorAnimationAssetEditing.attach(model, library, selected.id(), tracks.getFirst());
            });
        }
        ImGui.separator();
        ImGui.inputText("导入 ID（留空保留原 ID）##assetImportId", importId);
        if (ImGui.button("导入动画文件##assetImport")) {
            try {
                String file = chooseFile(false);
                if (file != null) {
                    selected = ActorAnimationAssetEditing.importFile(model, library, java.nio.file.Path.of(file), importId.get());
                    name.set(selected.name());
                    targetId.set(selected.id() + "_copy");
                    settings.setAssetId(selected.id());
                    message = "导入完成，可撤销。通过放到播放头复用此动作。";
                }
            } catch (java.io.IOException | RuntimeException e) {
                message = "导入失败：" + e.getMessage();
                org.slf4j.LoggerFactory.getLogger(ActorAnimationAssetPanel.class).warn("Animation import failed", e);
            }
        }
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
        } catch (IllegalArgumentException e) {
            message = e.getMessage();
        }
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

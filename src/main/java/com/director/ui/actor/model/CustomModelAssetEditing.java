package com.director.ui.actor.model;

import com.director.actor.model.BoneCuboid;
import com.director.actor.model.CustomModelDefinition;
import com.director.actor.model.CustomModelLibrary;
import com.director.actor.model.CustomModelPersistence;
import com.director.actor.model.texture.CuboidFaceUvs;
import com.director.actor.model.texture.CustomModelAtlasDimensions;
import com.director.actor.model.texture.CustomModelItemModelResolver;
import com.director.actor.model.texture.CustomModelResourceCache;
import com.director.actor.model.texture.CustomModelResourceService;
import com.director.actor.model.texture.CustomModelTexturePaths;
import com.director.timeline.model.TimelineModel;
import com.director.ui.timeline.commands.StateChangeCommand;
import com.director.ui.timeline.commands.TimelineCommandBridge;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Undoable edits to persisted custom model cuboid texture/UV data. */
public final class CustomModelAssetEditing {
    private CustomModelAssetEditing() {}

    public static void updateTexturedCuboid(
            TimelineModel model,
            String modelId,
            int cuboidIndex,
            String textureId,
            CuboidFaceUvs faceUvs) {
        Objects.requireNonNull(model, "model");
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("模型 ID 不能为空");
        }
        if (textureId == null || textureId.isBlank()) {
            throw new IllegalArgumentException("贴图 ID 不能为空");
        }
        if (faceUvs == null || !faceUvs.hasAnyFace()) {
            throw new IllegalArgumentException("至少需要一个面的 UV");
        }

        CustomModelLibrary library = CustomModelLibrary.getInstance();
        CustomModelDefinition before = library.find(modelId)
                .orElseThrow(() -> new IllegalArgumentException("未找到自定义模型: " + modelId));
        if (cuboidIndex < 0 || cuboidIndex >= before.cuboids().size()) {
            throw new IllegalArgumentException("无效的 cuboid 索引: " + cuboidIndex);
        }

        CustomModelDefinition after = withUpdatedCuboid(
                before,
                cuboidIndex,
                textureId.trim(),
                faceUvs);

        TimelineCommandBridge.execute(new StateChangeCommand(model, "Edit custom model cuboid UV",
                () -> applyModel(library, before),
                () -> applyModel(library, after)));
    }

    private static CustomModelDefinition withUpdatedCuboid(
            CustomModelDefinition model,
            int cuboidIndex,
            String textureId,
            CuboidFaceUvs faceUvs) {
        BoneCuboid current = model.cuboids().get(cuboidIndex);
        BoneCuboid draft = new BoneCuboid(
                current.boneId(),
                current.offset(),
                current.size(),
                current.blockStateId(),
                null,
                textureId,
                faceUvs);
        Map<String, Path> textures = CustomModelTexturePaths.loadFromWorld(model.id());
        int[] atlas = CustomModelAtlasDimensions.resolve(model.id(), textures);
        CustomModelResourceCache.getInstance().loadForCurrentWorld();
        String itemModelId = CustomModelItemModelResolver.resolve(
                draft,
                model.id(),
                textureId,
                textures,
                atlas[0],
                atlas[1]);
        BoneCuboid updated = new BoneCuboid(
                current.boneId(),
                current.offset(),
                current.size(),
                current.blockStateId(),
                itemModelId,
                textureId,
                faceUvs);

        List<BoneCuboid> cuboids = new ArrayList<>(model.cuboids());
        cuboids.set(cuboidIndex, updated);
        return new CustomModelDefinition(model.id(), model.displayName(), model.rigId(), cuboids);
    }

    private static void applyModel(CustomModelLibrary library, CustomModelDefinition definition) {
        library.register(definition);
        CustomModelPersistence.saveToWorld(library);
        rebuildClientResources(definition);
    }

    private static void rebuildClientResources(CustomModelDefinition definition) {
        try {
            if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
                return;
            }
            Map<String, Path> textures = CustomModelTexturePaths.loadFromWorld(definition.id());
            int[] atlas = CustomModelAtlasDimensions.resolve(definition.id(), textures);
            CustomModelResourceService.buildForModel(
                    definition,
                    textures,
                    atlas[0],
                    atlas[1],
                    true);
        } catch (RuntimeException ignored) {
            // Unit tests and dedicated servers do not have a Fabric client runtime.
        }
    }
}

package com.director.actor.model.texture;

import com.director.actor.model.BoneCuboid;
import com.director.actor.model.CustomModelDefinition;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Generates client resources for textured custom models and schedules a debounced reload. */
public final class CustomModelResourceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomModelResourceService.class);
    private static final int DEFAULT_ATLAS_SIZE = 64;

    private CustomModelResourceService() {}

    public static List<Identifier> buildForModel(
            CustomModelDefinition model,
            Map<String, Path> extractedTextures,
            int atlasWidth,
            int atlasHeight) {
        List<Identifier> generatedModels = new ArrayList<>();
        boolean wroteAny = false;
        for (BoneCuboid cuboid : model.cuboids()) {
            if (!cuboid.usesCustomTexture() || cuboid.textureId() == null || cuboid.textureId().isBlank()) {
                continue;
            }
            Path textureFile = extractedTextures.get(cuboid.textureId());
            if (textureFile == null || !textureFile.toFile().exists()) {
                LOGGER.warn("[CustomModelResourceService] 缺少贴图文件 textureId={} model={}",
                        cuboid.textureId(), model.id());
                continue;
            }
            try {
                String pieceKey = CustomModelResourceBuilder.pieceKey(cuboid, model.id(), cuboid.textureId());
                GeneratedItemModel generated = CustomModelResourceBuilder.build(
                        model.id(), pieceKey, cuboid, cuboid.textureId(), atlasWidth, atlasHeight);
                DirectorRuntimeResourcePack.writeTexture(textureFile, model.id(), cuboid.textureId());
                DirectorRuntimeResourcePack.writeModel(generated);
                generatedModels.add(generated.modelId());
                wroteAny = true;
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("[CustomModelResourceService] 生成 cuboid 资源失败 bone={} model={}",
                        cuboid.boneId(), model.id(), e);
            }
        }
        if (wroteAny) {
            DirectorRuntimeResourcePack.scheduleReload();
        }
        return generatedModels;
    }

    public static List<Identifier> buildForModel(CustomModelDefinition model, Map<String, Path> extractedTextures) {
        return buildForModel(model, extractedTextures, DEFAULT_ATLAS_SIZE, DEFAULT_ATLAS_SIZE);
    }
}

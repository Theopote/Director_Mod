package com.director.actor.model.texture;

import com.director.actor.model.BoneCuboid;
import com.director.actor.model.CustomModelDefinition;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
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
        return buildForModelIdentifiers(model, extractedTextures, atlasWidth, atlasHeight, true);
    }

    public static boolean buildForModel(
            CustomModelDefinition model,
            Map<String, Path> extractedTextures,
            boolean scheduleReload) {
        return buildForModel(model, extractedTextures, DEFAULT_ATLAS_SIZE, DEFAULT_ATLAS_SIZE, scheduleReload);
    }

    public static boolean buildForModel(
            CustomModelDefinition model,
            Map<String, Path> extractedTextures,
            int atlasWidth,
            int atlasHeight,
            boolean scheduleReload) {
        BuildResult result = buildForModelInternal(
                model, extractedTextures, atlasWidth, atlasHeight, scheduleReload);
        return result.wroteAny();
    }

    static List<Identifier> buildForModelIdentifiers(
            CustomModelDefinition model,
            Map<String, Path> extractedTextures,
            int atlasWidth,
            int atlasHeight,
            boolean scheduleReload) {
        return buildForModelInternal(model, extractedTextures, atlasWidth, atlasHeight, scheduleReload).models();
    }

    private record BuildResult(List<Identifier> models, boolean wroteAny) {}

    private static BuildResult buildForModelInternal(
            CustomModelDefinition model,
            Map<String, Path> extractedTextures,
            int atlasWidth,
            int atlasHeight,
            boolean scheduleReload) {
        CustomModelResourceCache cache = CustomModelResourceCache.getInstance();
        cache.loadForCurrentWorld();

        List<Identifier> generatedModels = new ArrayList<>();
        boolean wroteAny = false;
        for (BoneCuboid cuboid : model.cuboids()) {
            if (!cuboid.usesCustomTexture() || cuboid.textureId() == null || cuboid.textureId().isBlank()) {
                continue;
            }
            Path textureFile = extractedTextures.get(cuboid.textureId());
            if (textureFile == null || !Files.isRegularFile(textureFile)) {
                LOGGER.warn("[CustomModelResourceService] 缺少贴图文件 textureId={} model={}",
                        cuboid.textureId(), model.id());
                continue;
            }
            try {
                String textureHash = CustomModelResourceCache.hashFile(textureFile);
                CustomModelResourceCache.CacheEntry entry = cache.resolve(
                        cuboid, model.id(), cuboid.textureId(), textureHash, atlasWidth, atlasHeight);
                wroteAny |= ensureRuntimeTexture(textureFile, model.id(), cuboid.textureId());
                if (CustomModelResourceCache.hasRuntimeArtifacts(entry)) {
                    generatedModels.add(Identifier.of("director", "custom_model_piece/" + entry.pieceKey()));
                    continue;
                }
                GeneratedItemModel generated = CustomModelResourceBuilder.build(
                        model.id(), entry.pieceKey(), cuboid, cuboid.textureId(), atlasWidth, atlasHeight);
                DirectorRuntimeResourcePack.writeModel(generated);
                generatedModels.add(generated.modelId());
                wroteAny = true;
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("[CustomModelResourceService] 生成 cuboid 资源失败 bone={} model={}",
                        cuboid.boneId(), model.id(), e);
            }
        }
        if (wroteAny && scheduleReload) {
            DirectorRuntimeResourcePack.scheduleReload();
        }
        return new BuildResult(generatedModels, wroteAny);
    }

    public static List<Identifier> buildForModel(CustomModelDefinition model, Map<String, Path> extractedTextures) {
        return buildForModel(model, extractedTextures, DEFAULT_ATLAS_SIZE, DEFAULT_ATLAS_SIZE);
    }

    private static boolean ensureRuntimeTexture(Path sourcePng, String modelId, String textureKey) throws IOException {
        Path target = DirectorRuntimeResourcePack.texturePath(modelId, textureKey);
        if (Files.isRegularFile(target)) {
            return false;
        }
        DirectorRuntimeResourcePack.writeTexture(sourcePng, modelId, textureKey);
        return true;
    }
}

package com.director.actor.model.texture;

import com.director.actor.model.*;
import com.director.util.WorldIdentifier;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;

/** Client-side batch warmup for persisted custom model textures. */
public final class CustomModelResourceWarmup {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomModelResourceWarmup.class);
    private static volatile boolean scheduled;
    private static volatile String loadedModelsWorldKey;

    private CustomModelResourceWarmup() {}

    public static void scheduleForCurrentWorld() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return;
        }
        if (scheduled) {
            return;
        }
        scheduled = true;
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client == null) {
            scheduled = false;
            return;
        }
        client.execute(() -> {
            scheduled = false;
            try {
                warmup(ensureLibraryLoaded());
            } catch (RuntimeException e) {
                LOGGER.warn("[CustomModelResourceWarmup] 预热自定义模型贴图失败", e);
            }
        });
    }

    public static void warmup(CustomModelLibrary library) {
        if (library == null || library.all().isEmpty()) {
            return;
        }
        CustomModelResourceCache cache = CustomModelResourceCache.getInstance();
        cache.loadForCurrentWorld();
        boolean wroteAny = false;
        int warmedModels = 0;
        for (CustomModelDefinition model : library.all()) {
            if (!hasTexturedCuboid(model)) {
                continue;
            }
            warmedModels++;
            Map<String, Path> textures = CustomModelTexturePaths.loadFromWorld(model.id());
            int[] atlas = CustomModelAtlasDimensions.resolve(model.id(), textures);
            wroteAny |= CustomModelResourceService.buildForModel(
                    model,
                    textures,
                    atlas[0],
                    atlas[1],
                    false);
        }
        if (wroteAny) {
            DirectorRuntimeResourcePack.scheduleReload();
        }
        if (warmedModels > 0) {
            LOGGER.info("[CustomModelResourceWarmup] 已预热 {} 个带贴图的自定义模型", warmedModels);
        }
    }

    static CustomModelLibrary ensureLibraryLoaded() {
        String worldKey = WorldIdentifier.getCurrentWorldId();
        CustomModelLibrary library = CustomModelLibrary.getInstance();
        if (!worldKey.equals(loadedModelsWorldKey)) {
            CustomModelPersistence.loadFromWorld(library);
            BuiltInCustomModels.registerDefaults();
            loadedModelsWorldKey = worldKey;
        }
        return library;
    }

    private static boolean hasTexturedCuboid(CustomModelDefinition model) {
        return model.cuboids().stream().anyMatch(BoneCuboid::usesCustomTexture);
    }
}

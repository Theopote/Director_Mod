package com.director.actor.model.texture;

import com.director.actor.model.BuiltInCustomModels;
import com.director.actor.model.CustomModelDefinition;
import com.director.actor.model.CustomModelLibrary;
import com.director.actor.model.CustomModelPersistence;
import com.director.util.WorldIdentifier;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Client-side batch warmup for persisted custom model textures. */
public final class CustomModelResourceWarmup {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomModelResourceWarmup.class);
    private static final int DEFAULT_ATLAS_SIZE = 64;
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
            int[] atlas = resolveAtlasDimensions(cache, model.id(), textures);
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

    private static int[] resolveAtlasDimensions(
            CustomModelResourceCache cache,
            String modelId,
            Map<String, Path> textures) {
        return cache.findAtlasForModel(modelId)
                .or(() -> readAtlasFromTextures(textures))
                .orElse(new int[] {DEFAULT_ATLAS_SIZE, DEFAULT_ATLAS_SIZE});
    }

    private static java.util.Optional<int[]> readAtlasFromTextures(Map<String, Path> textures) {
        for (Path texture : textures.values()) {
            try {
                int[] size = readPngDimensions(texture);
                if (size != null) {
                    return java.util.Optional.of(size);
                }
            } catch (IOException ignored) {
                // try next texture
            }
        }
        return java.util.Optional.empty();
    }

    private static int[] readPngDimensions(Path file) throws IOException {
        byte[] header = Files.readAllBytes(file);
        if (header.length < 24
                || header[0] != (byte) 0x89
                || header[1] != 0x50
                || header[2] != 0x4E
                || header[3] != 0x47) {
            return null;
        }
        int width = readIntBigEndian(header, 16);
        int height = readIntBigEndian(header, 20);
        if (width <= 0 || height <= 0) {
            return null;
        }
        return new int[] {width, height};
    }

    private static int readIntBigEndian(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private static boolean hasTexturedCuboid(CustomModelDefinition model) {
        return model.cuboids().stream().anyMatch(cuboid -> cuboid.usesCustomTexture());
    }
}

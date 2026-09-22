package com.director.actor.model.texture;

import com.director.util.WorldIdentifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Resolves persisted Blockbench texture PNG paths inside the current world save. */
public final class CustomModelTexturePaths {
    private CustomModelTexturePaths() {}

    public static Path worldTextureRoot(String modelId) {
        Path save = WorldIdentifier.getWorldSavePath();
        if (save == null || modelId == null || modelId.isBlank()) {
            return null;
        }
        return save.resolve("director").resolve("custom_model_textures").resolve(sanitize(modelId));
    }

    public static Map<String, Path> loadFromWorld(String modelId) {
        Path root = worldTextureRoot(modelId);
        if (root == null || !Files.isDirectory(root)) {
            return Map.of();
        }
        Map<String, Path> textures = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(root)) {
            for (Path file : files.toList()) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                String name = file.getFileName().toString();
                if (!name.toLowerCase(Locale.ROOT).endsWith(".png")) {
                    continue;
                }
                String textureId = name.substring(0, name.length() - 4);
                textures.put(textureId, file);
            }
        } catch (IOException ignored) {
            return Map.of();
        }
        return textures;
    }

    private static String sanitize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
    }
}

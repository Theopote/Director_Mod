package com.director.actor.model.texture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/** Resolves texture atlas dimensions for custom model resource generation. */
public final class CustomModelAtlasDimensions {
    private static final int DEFAULT_ATLAS_SIZE = 64;

    private CustomModelAtlasDimensions() {}

    public static int[] resolve(String modelId, Map<String, Path> textures) {
        CustomModelResourceCache cache = CustomModelResourceCache.getInstance();
        cache.loadForCurrentWorld();
        return cache.findAtlasForModel(modelId)
                .or(() -> readFromTextures(textures))
                .orElse(new int[] {DEFAULT_ATLAS_SIZE, DEFAULT_ATLAS_SIZE});
    }

    private static Optional<int[]> readFromTextures(Map<String, Path> textures) {
        if (textures == null || textures.isEmpty()) {
            return Optional.empty();
        }
        for (Path texture : textures.values()) {
            try {
                int[] size = readPngDimensions(texture);
                if (size != null) {
                    return Optional.of(size);
                }
            } catch (IOException ignored) {
                // try next texture
            }
        }
        return Optional.empty();
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
}

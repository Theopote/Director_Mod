package com.director.actor.model.texture;

import com.director.actor.model.BoneCuboid;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Resolves generated item model ids for textured cuboids via the content-hash cache. */
public final class CustomModelItemModelResolver {
    private CustomModelItemModelResolver() {}

    public static String resolve(
            BoneCuboid draft,
            String modelId,
            String textureKey,
            Map<String, Path> textures,
            int atlasWidth,
            int atlasHeight) {
        Path textureFile = textures == null ? null : textures.get(textureKey);
        if (textureFile != null && Files.isRegularFile(textureFile)) {
            try {
                String textureHash = CustomModelResourceCache.hashFile(textureFile);
                CustomModelResourceCache.CacheEntry entry = CustomModelResourceCache.getInstance().resolve(
                        draft, modelId, textureKey, textureHash, atlasWidth, atlasHeight);
                return entry.itemModelId();
            } catch (IOException | RuntimeException ignored) {
                // Fall back to geometry-only key when texture hashing is unavailable.
            }
        }
        String contentKey = CustomModelResourceBuilder.contentKey(
                draft, atlasWidth, atlasHeight, "missing_texture");
        return CustomModelResourceCache.itemModelId(
                CustomModelResourceBuilder.pieceKeyFromContentKey(contentKey));
    }
}

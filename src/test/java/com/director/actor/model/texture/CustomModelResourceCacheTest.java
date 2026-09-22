package com.director.actor.model.texture;

import com.director.actor.model.BoneCuboid;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CustomModelResourceCacheTest {
    @Test
    void resolvesSamePieceKeyForIdenticalCuboidContent() {
        CuboidFaceUvs faceUvs = CuboidFaceUvs.uniform(new float[] {0, 0, 16, 16});
        BoneCuboid cuboid = new BoneCuboid(
                "torso",
                new Vector3f(0f, 0f, 0f),
                new Vector3f(1f, 1f, 1f),
                "minecraft:white_concrete",
                null,
                "0",
                faceUvs);
        CustomModelResourceCache cache = CustomModelResourceCache.getInstance();

        CustomModelResourceCache.CacheEntry first = cache.resolve(
                cuboid, "project:robot", "0", "abc123", 128, 128);
        CustomModelResourceCache.CacheEntry second = cache.resolve(
                cuboid, "project:other", "0", "abc123", 128, 128);

        assertEquals(first.pieceKey(), second.pieceKey());
        assertEquals(first.itemModelId(), second.itemModelId());
        assertEquals(128, first.atlasWidth());
        assertTrue(cache.find(CustomModelResourceBuilder.contentKey(cuboid, 128, 128, "abc123")).isPresent());
        assertTrue(cache.findAtlasForModel("project:robot").isPresent());
        assertEquals(128, cache.findAtlasForModel("project:robot").orElseThrow()[0]);
    }

    @Test
    void contentKeyChangesWhenTextureHashChanges() {
        BoneCuboid cuboid = new BoneCuboid(
                "torso",
                new Vector3f(0f, 0f, 0f),
                new Vector3f(1f, 1f, 1f),
                "minecraft:white_concrete",
                null,
                "0",
                CuboidFaceUvs.uniform(new float[] {0, 0, 16, 16}));
        String first = CustomModelResourceBuilder.contentKey(cuboid, 64, 64, "hash_a");
        String second = CustomModelResourceBuilder.contentKey(cuboid, 64, 64, "hash_b");
        assertNotEquals(first, second);
        assertEquals(64, first.length());
    }
}

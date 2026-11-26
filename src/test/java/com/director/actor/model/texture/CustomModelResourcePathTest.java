package com.director.actor.model.texture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomModelResourcePathTest {
    @Test
    void itemModelIdUsesCustomModelPiecePrefix() {
        String pieceKey = "abcdef0123456789";
        assertEquals("director:custom_model_piece/" + pieceKey, CustomModelResourceCache.itemModelId(pieceKey));
        assertTrue(CustomModelResourceCache.itemModelId(pieceKey).endsWith("/" + pieceKey));
    }

    @Test
    void sanitizeKeepsHyphenConsistently() {
        assertEquals("my-robot", DirectorRuntimeResourcePack.sanitize("My-Robot"));
        assertEquals("skin_01", DirectorRuntimeResourcePack.sanitize("Skin 01"));
    }
}
